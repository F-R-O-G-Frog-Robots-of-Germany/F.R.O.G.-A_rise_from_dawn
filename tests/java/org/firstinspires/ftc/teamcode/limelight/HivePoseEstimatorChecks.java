package org.firstinspires.ftc.teamcode.limelight;

import java.util.Random;

/** Forward-generated observations test the inverse hinge calculation without robot hardware. */
public final class HivePoseEstimatorChecks {
    private static int checks;
    private static final double[] IDENTITY = {1,0,0, 0,1,0, 0,0,1};
    private static final double[] DOWN = {0,0,-1};

    public static void main(String[] args) {
        upper_and_lower();
        arbitrary_mount_and_robot_tilt();
        invalid_inputs_and_copies();
        geometry_defaults();
        System.out.println("Hive pose estimator checks passed: " + checks);
    }

    private static void upper_and_lower() {
        HivePoseEstimator.TagModel model = model(new double[]{60,0,40}, new double[]{0,1,0},
                new double[]{-20,0,0}, new double[]{0,1,0}, false);
        HivePoseEstimator estimator = new HivePoseEstimator(new HivePoseEstimator.TagModel[]{model},
                new HivePoseEstimator.CameraMount(IDENTITY, new double[]{0,0,10}, false));
        for (int angle : new int[]{-30,0,30}) {
            double radians = Math.toRadians(angle);
            double[] tag = {60+20*Math.cos(radians),0,30+20*Math.sin(radians)};
            HivePoseEstimator.Result result = estimator.estimate(new HivePoseEstimator.Observation(
                    30, ry(-radians), tag, 100), DOWN);
            check(result != null, "intermediate and both tilted positions yield a pose");
            near(0, result.x, "same robot X throughout circular motion");
            near(0, result.y, "same robot Y throughout circular motion");
            near(0, result.z, "robot stays on floor");
            near(0, result.heading, "same heading throughout circular motion");
            check(!result.usesAssumedValues && result.captureTimeMs == 100, "exact input provenance retained");
        }
        HivePoseEstimator.TagModel written = model(new double[]{100,100,40}, new double[]{1,0,0},
                new double[]{0,0,20}, new double[]{0,1,0}, false);
        HivePoseEstimator writtenEstimator = new HivePoseEstimator(new HivePoseEstimator.TagModel[]{written},
                new HivePoseEstimator.CameraMount(IDENTITY, new double[]{4,2,10}, false));
        HivePoseEstimator.Result result = writtenEstimator.estimate(new HivePoseEstimator.Observation(
                30, ry(-Math.PI/6), new double[]{70,8,30-10*Math.sqrt(3)}, 100), DOWN);
        check(result != null, "complete documented example");
        near(90, result.x, "documented robot X");
        near(164, result.y, "documented robot Y");
        near(0, result.z, "documented robot Z");
        near(-Math.PI/2, result.heading, "documented heading is derived");
    }

    private static void arbitrary_mount_and_robot_tilt() {
        Random random = new Random(20261018);
        for (int sample = 0; sample < 1500; sample++) {
            double yaw = sample == 0 ? Math.PI : sample == 1 ? -Math.PI
                    : (random.nextDouble()*2-1)*Math.PI;
            double[] robot = {random.nextDouble()*144-72,random.nextDouble()*144-72,0};
            double[] robotRotation = mm(rz(yaw), mm(ry(random.nextDouble()*.4-.2),rx(random.nextDouble()*.4-.2)));
            double[] mounting = {3,-2,10};
            double[] cameraRotation = mm(rz(.25),mm(ry(-.3),rx(.05)));
            double[] worldCameraRotation = mm(robotRotation,cameraRotation);
            double[] camera = add(robot,mv(robotRotation,mounting));
            double[] pivot = {random.nextBoolean() ? 12.75 : -12.75,0,43.95};
            double axisYaw = random.nextDouble()*2*Math.PI;
            double[] axis = {Math.cos(axisYaw),Math.sin(axisYaw),0};
            double[] referenceRotation = mm(rz(-.2),mm(ry(.5),rx(.9)));
            double[] referenceTagOffset = {random.nextDouble()*12-6,14,-3};
            double[] hiveRotation = around(axis,random.nextDouble()*2*Math.PI-Math.PI);
            double[] worldTag = add(pivot,mv(hiveRotation,referenceTagOffset));
            double[] worldTagRotation = mm(hiveRotation,referenceRotation);
            HivePoseEstimator.TagModel model = model(pivot,axis,
                    negate(mv(transpose(referenceRotation),referenceTagOffset)),
                    mv(transpose(referenceRotation),axis),false);
            HivePoseEstimator estimator = new HivePoseEstimator(new HivePoseEstimator.TagModel[]{model},
                    new HivePoseEstimator.CameraMount(cameraRotation,mounting,false));
            HivePoseEstimator.Observation observation = new HivePoseEstimator.Observation(30,
                    mm(transpose(worldCameraRotation),worldTagRotation),
                    mv(transpose(worldCameraRotation),subtract(worldTag,camera)),100);
            double[] gravity = mv(transpose(robotRotation),DOWN);
            // Magnitude is immaterial: inputs can be a unit direction or gravity-scaled direction.
            for (int i=0;i<3;i++) gravity[i]*=9.81;
            HivePoseEstimator.Result result = estimator.estimate(observation,gravity);
            check(result != null, "random full pose reconstructs");
            near(robot[0],result.x,"random X");
            near(robot[1],result.y,"random Y");
            near(0,result.z,"random floor height");
            near(0,Math.atan2(Math.sin(result.heading-yaw),Math.cos(result.heading-yaw)),"random yaw and wrap");
            near(0,result.axisErrorDeg,"exact axis and gravity agree");
        }
    }

    private static void invalid_inputs_and_copies() {
        double[] rotation = IDENTITY.clone(), translation = {60,0,10};
        double[] pivot = {60,0,40}, offset = {0,0,20}, axis = {0,1,0};
        HivePoseEstimator.TagModel model = model(pivot,axis,offset,axis,true);
        HivePoseEstimator.TagModel[] models = {model};
        double[] cameraOffset = {0,0,10};
        HivePoseEstimator estimator = new HivePoseEstimator(models,
                new HivePoseEstimator.CameraMount(rotation,cameraOffset,false));
        HivePoseEstimator.Observation observation = new HivePoseEstimator.Observation(30,rotation,translation,100);
        rotation[0]=2; translation[0]=999; pivot[0]=999; offset[2]=999;
        axis[1]=0; cameraOffset[0]=999; models[0]=null;
        observation.rotation_camera_tag()[0]=999;
        observation.translation_camera_tag()[0]=999;
        model.pivot_tag()[2]=999; model.axis_world()[1]=999;
        HivePoseEstimator.Result valid = estimator.estimate(observation,DOWN);
        check(valid != null && valid.usesAssumedValues,"defensive copies and assumed-model provenance");
        near(0,valid.x,"copies preserve X");
        near(0,valid.z,"copies preserve height");
        check(estimator.estimate(null,DOWN)==null,"missing observation");
        check(estimator.estimate(observation,null)==null,"missing gravity");
        check(estimator.estimate(observation,new double[]{0,0,0})==null,"zero gravity");
        check(estimator.estimate(observation,new double[]{0,1,0})==null,"axis parallel to gravity");
        check(estimator.estimate(observation,new double[]{0,Double.NaN,-1})==null,"nonfinite gravity");
        check(estimator.estimate(new HivePoseEstimator.Observation(99,IDENTITY,new double[]{0,0,1},100),DOWN)==null,
                "unknown tag");
        check(estimator.estimate(new HivePoseEstimator.Observation(30,IDENTITY,new double[]{0,0,1},-1),DOWN)==null,
                "invalid timestamp");
        check(estimator.estimate(new HivePoseEstimator.Observation(30,IDENTITY,new double[]{Double.NaN,0,1},100),DOWN)==null,
                "nonfinite measurement");
        double[] reflection = {-1,0,0,0,1,0,0,0,1};
        check(estimator.estimate(new HivePoseEstimator.Observation(30,reflection,new double[]{0,0,1},100),DOWN)==null,
                "reflection is not a rotation");
        rejects(() -> new HivePoseEstimator.CameraMount(reflection,new double[]{0,0,0},false),"bad mounting");
        rejects(() -> model(new double[]{0,0,0},DOWN,new double[]{0,0,1},DOWN,false),"vertical world hinge");
        rejects(() -> new HivePoseEstimator(new HivePoseEstimator.TagModel[]{model,model},
                new HivePoseEstimator.CameraMount(IDENTITY,new double[]{0,0,0},false)),"duplicate IDs");
        HivePoseEstimator exact = new HivePoseEstimator(new HivePoseEstimator.TagModel[]{
                model(new double[]{60,0,40},new double[]{0,1,0},new double[]{0,0,20},new double[]{0,1,0},false)},
                new HivePoseEstimator.CameraMount(IDENTITY,new double[]{0,0,10},false));
        check(exact.estimate(new HivePoseEstimator.Observation(30,IDENTITY,new double[]{60,0,10},100,true),DOWN)
                .usesAssumedValues,"estimated timestamp marked");
        check(exact.estimate(observation,DOWN,true).usesAssumedValues,"assumed level gravity marked");
        HivePoseEstimator wrongHeight = new HivePoseEstimator(new HivePoseEstimator.TagModel[]{
                model(new double[]{60,0,50},new double[]{0,1,0},new double[]{0,0,20},new double[]{0,1,0},false)},
                new HivePoseEstimator.CameraMount(IDENTITY,new double[]{0,0,10},false));
        near(10,wrongHeight.estimate(observation,DOWN).z,"height mismatch stays visible, never silently clamped");
    }

    private static void geometry_defaults() {
        HivePoseEstimator.TagModel[] models = HiveGeometry.tag_models();
        check(models.length==16,"all official tag IDs represented");
        for (int i=0;i<models.length;i++) {
            HivePoseEstimator.TagModel model=models[i];
            check(model.tagId==30+i && model.usesAssumedGeometry,"default ID and unverified mapping flagged");
            near(i<8?-12.75:12.75,model.pivot_world()[0],"physical alliance pivot, not shooting pose");
            near(43.95,model.pivot_world()[2],"manual hinge height");
            near(14.269059480313,model.pivot_tag()[1],"CAD in-plane offset");
            near(1.498167842276,model.pivot_tag()[2],"CAD normal plus explicitly assumed printed face");
            boolean audience=model.tagId>=34 && model.tagId<=41;
            near(audience?1:-1,model.axis_tag()[0],"opposite Cell signed axes");
        }
    }

    private static HivePoseEstimator.TagModel model(double[] pivot,double[] worldAxis,double[] tagOffset,
                                                    double[] tagAxis,boolean assumed) {
        return new HivePoseEstimator.TagModel(30,pivot,worldAxis,tagOffset,tagAxis,assumed,"test fixture");
    }
    private static double[] rx(double a) { double c=Math.cos(a),s=Math.sin(a); return new double[]{1,0,0,0,c,-s,0,s,c}; }
    private static double[] ry(double a) { double c=Math.cos(a),s=Math.sin(a); return new double[]{c,0,s,0,1,0,-s,0,c}; }
    private static double[] rz(double a) { double c=Math.cos(a),s=Math.sin(a); return new double[]{c,-s,0,s,c,0,0,0,1}; }
    private static double[] around(double[] a,double angle) {
        double c=Math.cos(angle),s=Math.sin(angle),v=1-c,x=a[0],y=a[1],z=a[2];
        return new double[]{c+x*x*v,x*y*v-z*s,x*z*v+y*s,
                y*x*v+z*s,c+y*y*v,y*z*v-x*s,z*x*v-y*s,z*y*v+x*s,c+z*z*v};
    }
    private static double[] mv(double[] m,double[] v) {
        return new double[]{m[0]*v[0]+m[1]*v[1]+m[2]*v[2],m[3]*v[0]+m[4]*v[1]+m[5]*v[2],m[6]*v[0]+m[7]*v[1]+m[8]*v[2]};
    }
    private static double[] mm(double[] a,double[] b) {
        double[] result=new double[9];
        for(int row=0;row<3;row++)for(int col=0;col<3;col++)for(int k=0;k<3;k++)result[row*3+col]+=a[row*3+k]*b[k*3+col];
        return result;
    }
    private static double[] transpose(double[] a) { return new double[]{a[0],a[3],a[6],a[1],a[4],a[7],a[2],a[5],a[8]}; }
    private static double[] add(double[] a,double[] b) { return new double[]{a[0]+b[0],a[1]+b[1],a[2]+b[2]}; }
    private static double[] subtract(double[] a,double[] b) { return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]}; }
    private static double[] negate(double[] a) { return new double[]{-a[0],-a[1],-a[2]}; }
    private static void near(double expected,double actual,String message) {
        check(Double.isFinite(actual)&&Math.abs(expected-actual)<1e-8,message+": expected "+expected+", got "+actual);
    }
    private static void check(boolean condition,String message) {
        checks++; if(!condition)throw new AssertionError(message);
    }
    private static void rejects(Runnable action,String message) {
        boolean rejected=false; try{action.run();}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,message);
    }
}

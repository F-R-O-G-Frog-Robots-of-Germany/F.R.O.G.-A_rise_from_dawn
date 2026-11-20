package org.firstinspires.ftc.teamcode.limelight;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Hardware-free checks using observations generated forward from known robot and Hive poses. */
public final class HiveMultiTagChecks {
    private static int checks;
    private static final double[] IDENTITY = {1,0,0, 0,1,0, 0,0,1};
    private static final double[] DOWN = {0,0,-1};

    public static void main(String[] args) {
        single_tag_and_provenance();
        position_and_circular_heading_average();
        consensus_and_outliers();
        invalid_frames_and_axis_filter();
        configuration_and_defensive_results();
        independently_tilting_hives();
        independent_noise_reduces_position_error();
        System.out.println("Hive multi-tag checks passed: " + checks);
    }

    private static void single_tag_and_provenance() {
        HivePoseEstimator estimator = fixture();
        HivePoseEstimator.Observation observation = at(30,12,-7,2,.7);
        HivePoseEstimator.Result single = estimator.estimate(observation,DOWN);
        HivePoseEstimator.FrameResult frame = frame(estimator,observation);
        check(single != null && frame != null,"single usable tag still yields a frame pose");
        near(single.x,frame.x,"single-tag X preserved");
        near(single.y,frame.y,"single-tag Y preserved");
        near(single.z,frame.z,"single-tag Z preserved");
        angle_near(single.heading,frame.heading,"single-tag heading preserved");
        near(0,frame.positionSpreadIn,"singleton has zero position disagreement");
        near(0,frame.headingSpreadDeg,"singleton has zero heading disagreement");
        check(frame.tagCount==1 && frame.usableTagCount==1 && frame.observationCount==1,
                "singleton counts");
        check(frame.captureTimeMs==100 && !frame.usesAssumedValues,"exact frame provenance retained");
        check(estimator.estimate_frame(Arrays.asList(observation),DOWN,true).usesAssumedValues,
                "assumed gravity is visible on fused result");
        HivePoseEstimator.Observation estimatedTime = new HivePoseEstimator.Observation(31,
                observation.rotation_camera_tag(),observation.translation_camera_tag(),100,true);
        check(frame(estimator,observation,estimatedTime).usesAssumedValues,"accepted timing assumption propagates");
        HivePoseEstimator assumedGeometry = new HivePoseEstimator(new HivePoseEstimator.TagModel[]{simple_model(30,true)},
                new HivePoseEstimator.CameraMount(IDENTITY,new double[]{0,0,10},false));
        check(frame(assumedGeometry,observation).usesAssumedValues,"accepted geometry assumption propagates");
        HivePoseEstimator assumedMount = new HivePoseEstimator(new HivePoseEstimator.TagModel[]{simple_model(30,false)},
                new HivePoseEstimator.CameraMount(IDENTITY,new double[]{0,0,10},true));
        check(frame(assumedMount,observation).usesAssumedValues,"mount assumption propagates");
    }

    private static void position_and_circular_heading_average() {
        HivePoseEstimator estimator = fixture();
        HivePoseEstimator.FrameResult result = frame(estimator,
                at(32,12,3,4,0),at(30,8,1,2,0),at(31,10,2,3,0));
        check(result!=null && result.tagCount==3,"three nearby tags agree");
        near(10,result.x,"equal-weight X mean");
        near(2,result.y,"equal-weight Y mean");
        near(3,result.z,"height is averaged without clamping");
        near(2,result.positionSpreadIn,"spread is 3D RMS, not planar or mean absolute distance");
        near(0,result.headingSpreadDeg,"identical headings have zero spread");
        check(Arrays.equals(result.tag_ids(),new int[]{30,31,32}),"accepted IDs are sorted");

        result = frame(estimator,at(30,0,0,0,Math.toRadians(179)),
                at(31,0,0,0,Math.toRadians(-179)));
        check(result!=null,"headings across minus/plus pi agree");
        angle_near(Math.PI,result.heading,"circular mean stays at pi, not zero");
        near(1,result.headingSpreadDeg,"heading RMS wraps across pi");

        result = frame(estimator,at(30,0,0,0,Math.toRadians(-3)),
                at(31,0,0,0,0),at(32,0,0,0,Math.toRadians(3)));
        near(Math.sqrt(6),result.headingSpreadDeg,"heading spread is degrees RMS");
    }

    private static void consensus_and_outliers() {
        HivePoseEstimator estimator = fixture();
        HivePoseEstimator.FrameResult result = frame(estimator,
                at(33,50,50,0,0),at(32,11,5,0,.02),at(30,9,5,0,-.02),at(31,10,5,0,0));
        check(result!=null && result.tagCount==3 && result.usableTagCount==4 && result.observationCount==4,
                "unique strict majority excludes an otherwise usable outlier");
        near(10,result.x,"outlier does not bias position");
        near(5,result.y,"accepted position average");
        angle_near(0,result.heading,"outlier does not bias heading");
        check(Arrays.equals(result.tag_ids(),new int[]{30,31,32}),"rejected ID is absent");

        HivePoseEstimator.Observation assumedOutlier = new HivePoseEstimator.Observation(32,IDENTITY,
                new double[]{1,1,1},100,true);
        result = frame(estimator,at(30,0,0,0,0),at(31,1,0,0,0),assumedOutlier);
        check(result!=null && !result.usesAssumedValues,"rejected pose does not taint accepted provenance");
        check(frame(estimator,at(30,0,0,0,0),at(31,20,0,0,0))==null,
                "two conflicting tags cannot decide which is correct");
        check(frame(estimator,at(30,0,0,0,0),at(31,1,0,0,0),at(32,20,0,0,0),at(33,21,0,0,0))==null,
                "equally supported disjoint clusters rejected");
        check(frame(estimator,at(30,0,0,0,0),at(31,1,0,0,0),at(32,20,0,0,0),at(33,40,0,0,0))==null,
                "unique largest group still needs a strict majority");
        check(frame(estimator,at(30,0,0,0,0),at(31,5,0,0,0),at(32,10,0,0,0))==null,
                "bridge chain rejected because endpoints disagree");
        check(frame(estimator,at(30,0,0,0,0),at(31,4,0,0,0),at(32,8,0,0,0),at(33,12,0,0,0))==null,
                "equally large overlapping neighborhoods rejected");
        check(frame(estimator,at(30,0,0,0,0),at(31,0,0,7,0))==null,
                "vertical disagreement is included in the distance gate");
        check(frame(estimator,at(30,0,0,0,0),at(31,0,0,0,Math.toRadians(11)))==null,
                "same position does not override incompatible heading");

        List<HivePoseEstimator.Observation> observations = Arrays.asList(at(30,0,0,0,0),at(31,1,0,0,0),
                at(32,2,0,0,0),at(33,30,0,0,0));
        HivePoseEstimator.FrameResult expected = estimator.estimate_frame(observations,DOWN,false);
        List<HivePoseEstimator.Observation> shuffled = new ArrayList<>(observations);
        Random random = new Random(7283);
        for (int i=0;i<20;i++) {
            Collections.shuffle(shuffled,random);
            result = estimator.estimate_frame(shuffled,DOWN,false);
            check(result!=null && Arrays.equals(expected.tag_ids(),result.tag_ids()),"input order does not alter consensus");
            check(Double.doubleToLongBits(expected.x)==Double.doubleToLongBits(result.x),"input order does not alter sum order");
        }
    }

    private static void invalid_frames_and_axis_filter() {
        HivePoseEstimator estimator = fixture();
        HivePoseEstimator.Observation valid = at(30,0,0,0,0);
        HivePoseEstimator.Observation unknown = at(99,0,0,0,0);
        HivePoseEstimator.Observation reflected = new HivePoseEstimator.Observation(31,
                new double[]{-1,0,0,0,1,0,0,0,1},new double[]{80,0,30},100);
        HivePoseEstimator.Observation nonfinite = new HivePoseEstimator.Observation(32,IDENTITY,
                new double[]{Double.NaN,0,30},100);
        HivePoseEstimator.Observation tiltedAxis = axis_error(33,11);
        List<HivePoseEstimator.Observation> mixed = Arrays.asList(null,unknown,reflected,nonfinite,tiltedAxis,valid);
        HivePoseEstimator.FrameResult result = estimator.estimate_frame(mixed,DOWN,false);
        check(result!=null && result.tagCount==1 && result.usableTagCount==1 && result.observationCount==6,
                "unusable tags are excluded before majority vote and remain in input count");
        near(0,result.x,"bad tags do not bias singleton fallback");
        check(estimator.estimate_frame(null,DOWN,false)==null,"null list rejected");
        check(estimator.estimate_frame(Collections.<HivePoseEstimator.Observation>emptyList(),DOWN,false)==null,
                "empty frame rejected");
        check(frame(estimator,null,null)==null,"all-null frame rejected");
        check(frame(estimator,unknown,reflected,nonfinite,tiltedAxis)==null,"no usable tags rejected");
        check(estimator.estimate_frame(Arrays.asList(valid),new double[]{0,0,0},false)==null,"zero gravity rejected");
        check(estimator.estimate_frame(Arrays.asList(valid),null,false)==null,"missing gravity rejected");
        check(estimator.estimate_frame(Arrays.asList(valid),new double[]{0,Double.NaN,-1},false)==null,
                "nonfinite gravity rejected");
        check(frame(estimator,valid,valid)==null,"duplicate ID cannot get a second vote");
        check(frame(estimator,valid,unknown,unknown)==null,"duplicate unknown ID is also a malformed frame");
        check(frame(estimator,valid,new HivePoseEstimator.Observation(31,IDENTITY,new double[]{80,0,30},101))==null,
                "different timestamps cannot be fused");
        check(frame(estimator,valid,new HivePoseEstimator.Observation(99,IDENTITY,new double[]{80,0,30},101))==null,
                "timestamp consistency is checked before unknown tag filtering");
        check(frame(estimator,new HivePoseEstimator.Observation(30,IDENTITY,new double[]{80,0,30},-1))==null,
                "negative timestamp rejected");
        result = frame(estimator,axis_error(30,4),axis_error(31,8),axis_error(32,11));
        check(result!=null && result.tagCount==2 && result.usableTagCount==2,"default axis limit filters eleven degrees");
        near(8,result.maxAxisErrorDeg,"reported maximum includes only accepted axes");
        result = estimator.estimate_frame(Arrays.asList(axis_error(30,11)),DOWN,false,
                new HivePoseEstimator.FrameConfig(6,10,12));
        check(result!=null,"axis threshold is configurable");
        near(11,result.maxAxisErrorDeg,"custom threshold preserves axis diagnostic");
    }

    private static void configuration_and_defensive_results() {
        for (double bad : new double[]{-1,0,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            rejects(() -> new HivePoseEstimator.FrameConfig(bad,10,10),"invalid position limit");
        }
        for (double bad : new double[]{-1,0,90,100,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            rejects(() -> new HivePoseEstimator.FrameConfig(6,bad,10),"invalid heading limit");
        }
        for (double bad : new double[]{-1,90,100,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            rejects(() -> new HivePoseEstimator.FrameConfig(6,10,bad),"invalid axis limit");
        }
        HivePoseEstimator estimator = fixture();
        List<HivePoseEstimator.Observation> observations = new ArrayList<>(Arrays.asList(at(31,0,0,0,0),at(30,7,0,0,0)));
        check(estimator.estimate_frame(observations,DOWN,false)==null,"default six-inch separation limit applies");
        HivePoseEstimator.FrameResult result = estimator.estimate_frame(observations,DOWN,false,
                new HivePoseEstimator.FrameConfig(8,10,0));
        check(result!=null && result.tagCount==2,"custom position gate and zero axis tolerance accepted");
        int[] ids = result.tag_ids();
        ids[0]=999;
        observations.clear();
        check(Arrays.equals(result.tag_ids(),new int[]{30,31}),"result IDs are a defensive copy independent of input list");
        near(3.5,result.x,"result survives input-list mutation");
        rejects(() -> estimator.estimate_frame(Arrays.asList(at(30,0,0,0,0)),DOWN,false,null),
                "missing config rejected explicitly");
        result = estimator.estimate_frame(Arrays.asList(at(30,0,0,0,0),at(31,0,0,0,Math.toRadians(11))),DOWN,false,
                new HivePoseEstimator.FrameConfig(6,12,10));
        check(result!=null,"custom heading gate applies");
    }

    private static void independently_tilting_hives() {
        Random random = new Random(20261116);
        for (int sample=0;sample<400;sample++) {
            SyntheticFrame synthetic = synthetic_frame(random,0);
            HivePoseEstimator.FrameResult result = synthetic.estimator.estimate_frame(synthetic.observations,
                    synthetic.gravity,false);
            check(result!=null && result.tagCount==4,"four tags on two independently tilted Hives agree");
            near(synthetic.robot[0],result.x,"random reconstruction X");
            near(synthetic.robot[1],result.y,"random reconstruction Y");
            near(synthetic.robot[2],result.z,"random reconstruction Z");
            angle_near(synthetic.heading,result.heading,"random reconstruction heading");
            near(0,result.positionSpreadIn,"exact forward model yields zero position spread");
            near(0,result.headingSpreadDeg,"exact forward model yields zero heading spread");
            near(0,result.maxAxisErrorDeg,"tilted robot gravity removes apparent hinge slope");
            check(!result.usesAssumedValues && result.captureTimeMs==1234,"random measured gravity provenance retained");
        }
    }

    private static void independent_noise_reduces_position_error() {
        Random random = new Random(845127);
        double singleSquared=0,fusedSquared=0;
        int samples=1000;
        for (int sample=0;sample<samples;sample++) {
            // Independent camera-frame translation errors, bounded to +/-1 inch per axis.
            // Rotations, mount, gravity and tag models are exact in this controlled experiment.
            SyntheticFrame synthetic = synthetic_frame(random,1);
            HivePoseEstimator.FrameResult result = synthetic.estimator.estimate_frame(synthetic.observations,
                    synthetic.gravity,false);
            check(result!=null && result.tagCount==4,"bounded independent noise stays inside pairwise gates");
            for (HivePoseEstimator.Observation observation : synthetic.observations) {
                HivePoseEstimator.Result single = synthetic.estimator.estimate(observation,synthetic.gravity);
                singleSquared+=squared_error(synthetic.robot,single.x,single.y,single.z)/4;
            }
            fusedSquared+=squared_error(synthetic.robot,result.x,result.y,result.z);
        }
        double singleRms=Math.sqrt(singleSquared/samples),fusedRms=Math.sqrt(fusedSquared/samples);
        check(fusedRms<.6*singleRms && fusedRms>.4*singleRms,
                "four independent noisy tags reduce typical single-tag RMS to approximately one half");
        System.out.printf(java.util.Locale.ROOT,
                "Independent translation-noise experiment: single %.4f in RMS, four-tag %.4f in RMS (ratio %.3f)%n",
                singleRms,fusedRms,fusedRms/singleRms);
    }

    /** Generates two tags per Hive with independently chosen pivots, axes, tilt and tag reference frames. */
    private static SyntheticFrame synthetic_frame(Random random,double noise) {
        double yaw=range(random,-Math.PI,Math.PI);
        double[] robot={range(random,-72,72),range(random,-72,72),range(random,-2,2)};
        double[] robotRotation=mm(rz(yaw),mm(ry(range(random,-.2,.2)),rx(range(random,-.2,.2))));
        double[] mounting={3,-2,10};
        double[] cameraRotation=mm(rz(.25),mm(ry(-.3),rx(.05)));
        double[] worldCameraRotation=mm(robotRotation,cameraRotation);
        double[] camera=add(robot,mv(robotRotation,mounting));
        List<HivePoseEstimator.Observation> observations=new ArrayList<>();
        HivePoseEstimator.TagModel[] models=new HivePoseEstimator.TagModel[4];
        for (int hive=0;hive<2;hive++) {
            double[] pivot={range(random,-40,40),range(random,-40,40),range(random,30,50)};
            double axisYaw=range(random,-Math.PI,Math.PI);
            double[] axis={Math.cos(axisYaw),Math.sin(axisYaw),0};
            double[] hiveRotation=around(axis,range(random,-Math.PI,Math.PI));
            for (int tag=0;tag<2;tag++) {
                int index=hive*2+tag,id=30+index;
                double[] referenceRotation=mm(rz(range(random,-Math.PI,Math.PI)),
                        mm(ry(range(random,-1,1)),rx(range(random,-1,1))));
                double[] referenceOffset={range(random,-6,6),14,range(random,-5,5)};
                models[index]=new HivePoseEstimator.TagModel(id,pivot,axis,
                        negate(mv(transpose(referenceRotation),referenceOffset)),
                        mv(transpose(referenceRotation),axis),false,"synthetic independently tilting Hive");
                double[] worldTag=add(pivot,mv(hiveRotation,referenceOffset));
                double[] worldTagRotation=mm(hiveRotation,referenceRotation);
                double[] measuredTranslation=mv(transpose(worldCameraRotation),subtract(worldTag,camera));
                for (int i=0;i<3;i++) measuredTranslation[i]+=noise*range(random,-1,1);
                observations.add(new HivePoseEstimator.Observation(id,
                        mm(transpose(worldCameraRotation),worldTagRotation),measuredTranslation,1234));
            }
        }
        double[] gravity=mv(transpose(robotRotation),DOWN);
        for (int i=0;i<3;i++) gravity[i]*=9.81;
        return new SyntheticFrame(new HivePoseEstimator(models,
                new HivePoseEstimator.CameraMount(cameraRotation,mounting,false)),observations,gravity,robot,yaw);
    }

    private static final class SyntheticFrame {
        final HivePoseEstimator estimator;
        final List<HivePoseEstimator.Observation> observations;
        final double[] gravity,robot;
        final double heading;
        SyntheticFrame(HivePoseEstimator estimator,List<HivePoseEstimator.Observation> observations,
                       double[] gravity,double[] robot,double heading) {
            this.estimator=estimator; this.observations=observations; this.gravity=gravity;
            this.robot=robot; this.heading=heading;
        }
    }

    private static HivePoseEstimator fixture() {
        HivePoseEstimator.TagModel[] models=new HivePoseEstimator.TagModel[8];
        for (int i=0;i<models.length;i++) models[i]=simple_model(30+i,false);
        return new HivePoseEstimator(models,new HivePoseEstimator.CameraMount(IDENTITY,new double[]{0,0,10},false));
    }
    private static HivePoseEstimator.TagModel simple_model(int id,boolean assumed) {
        return new HivePoseEstimator.TagModel(id,new double[]{60,0,40},new double[]{0,1,0},
                new double[]{-20,0,0},new double[]{0,1,0},assumed,"controlled consensus fixture");
    }
    /** Exact forward observation at a chosen level robot pose, with a fixed neutral tag. */
    private static HivePoseEstimator.Observation at(int id,double x,double y,double z,double heading) {
        double[] cameraTag=rz(-heading);
        return new HivePoseEstimator.Observation(id,cameraTag,mv(cameraTag,new double[]{80-x,-y,30-z}),100);
    }
    private static HivePoseEstimator.Observation axis_error(int id,double degrees) {
        return new HivePoseEstimator.Observation(id,rx(Math.toRadians(degrees)),new double[]{80,0,30},100);
    }
    private static HivePoseEstimator.FrameResult frame(HivePoseEstimator estimator,HivePoseEstimator.Observation... observations) {
        return estimator.estimate_frame(Arrays.asList(observations),DOWN,false);
    }
    private static double squared_error(double[] expected,double x,double y,double z) {
        double dx=x-expected[0],dy=y-expected[1],dz=z-expected[2];
        return dx*dx+dy*dy+dz*dz;
    }
    private static double range(Random random,double low,double high) { return low+(high-low)*random.nextDouble(); }
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
    private static void angle_near(double expected,double actual,String message) {
        near(0,Math.atan2(Math.sin(actual-expected),Math.cos(actual-expected)),message);
    }
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

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;
import java.util.*;
import javax.tools.*;

/** Parse supplied Java source only. Never analyzes, compiles or executes imported programs. */
public final class JavaAstDump {
    private CompilationUnitTree unit;
    private SourcePositions positions;

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("--source-list")) {
            args = java.nio.file.Files.readAllLines(java.nio.file.Paths.get(args[1]),
                    java.nio.charset.StandardCharsets.UTF_8).toArray(new String[0]);
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("A full JDK is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT,
                java.nio.charset.StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> files = manager.getJavaFileObjects(args);
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    Arrays.asList("-proc:none", "-encoding", "UTF-8"), null, files);
            JavaAstDump dump = new JavaAstDump();
            dump.positions = Trees.instance(task).getSourcePositions();
            List<Object> units = new ArrayList<>();
            for (CompilationUnitTree tree : task.parse()) {
                dump.unit = tree;
                Map<String,Object> result = object("file", tree.getSourceFile().getName(),
                        "package", tree.getPackageName() == null ? "" : tree.getPackageName().toString());
                List<Object> imports = new ArrayList<>();
                for (ImportTree item : tree.getImports())
                    imports.add(object("name", item.getQualifiedIdentifier().toString(), "static", item.isStatic()));
                result.put("imports", imports);
                result.put("types", dump.nodes(tree.getTypeDecls()));
                units.add(result);
            }
            List<Object> problems = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> problem : diagnostics.getDiagnostics()) {
                problems.add(object("severity", problem.getKind() == Diagnostic.Kind.ERROR ? "error" : "warning",
                        "code", "JAVA_SYNTAX", "message", problem.getMessage(Locale.ROOT),
                        "file", problem.getSource() == null ? "" : problem.getSource().getName(),
                        "line", problem.getLineNumber()));
            }
            System.out.println(json(object("units", units, "diagnostics", problems)));
        }
    }

    private List<Object> nodes(Iterable<? extends Tree> values) {
        List<Object> result = new ArrayList<>();
        for (Tree value : values) result.add(node(value));
        return result;
    }

    private Map<String,Object> node(Tree tree) {
        if (tree == null) return null;
        long start = positions.getStartPosition(unit, tree);
        Map<String,Object> n = object("kind", tree.getKind().name(), "line",
                start < 0 ? 1 : unit.getLineMap().getLineNumber(start));
        if (tree instanceof ClassTree) {
            ClassTree t = (ClassTree)tree;
            n.put("name", t.getSimpleName().toString()); n.put("extends", text(t.getExtendsClause()));
            n.put("implements", strings(t.getImplementsClause()));
            n.put("modifiers", t.getModifiers().getFlags().toString());
            n.put("annotations", strings(t.getModifiers().getAnnotations()));
            n.put("members", nodes(t.getMembers()));
        } else if (tree instanceof MethodTree) {
            MethodTree t = (MethodTree)tree;
            n.put("name", t.getName().toString()); n.put("type", text(t.getReturnType()));
            n.put("parameters", nodes(t.getParameters())); n.put("body", node(t.getBody()));
            n.put("modifiers", t.getModifiers().getFlags().toString());
            n.put("varargs", t.toString().contains("..."));
        } else if (tree instanceof VariableTree) {
            VariableTree t = (VariableTree)tree;
            n.put("name", t.getName().toString()); n.put("type", text(t.getType()));
            n.put("value", node(t.getInitializer())); n.put("modifiers", t.getModifiers().getFlags().toString());
        } else if (tree instanceof BlockTree) {
            n.put("statements", nodes(((BlockTree)tree).getStatements()));
            n.put("static", ((BlockTree)tree).isStatic());
        } else if (tree instanceof ExpressionStatementTree) n.put("expression", node(((ExpressionStatementTree)tree).getExpression()));
        else if (tree instanceof ReturnTree) n.put("expression", node(((ReturnTree)tree).getExpression()));
        else if (tree instanceof ThrowTree) n.put("expression", node(((ThrowTree)tree).getExpression()));
        else if (tree instanceof LiteralTree) n.put("value", ((LiteralTree)tree).getValue());
        else if (tree instanceof IdentifierTree) n.put("name", ((IdentifierTree)tree).getName().toString());
        else if (tree instanceof MemberSelectTree) {
            n.put("object", node(((MemberSelectTree)tree).getExpression()));
            n.put("name", ((MemberSelectTree)tree).getIdentifier().toString());
        } else if (tree instanceof MethodInvocationTree) {
            MethodInvocationTree t = (MethodInvocationTree)tree;
            n.put("select", node(t.getMethodSelect())); n.put("args", nodes(t.getArguments()));
        } else if (tree instanceof NewClassTree) {
            NewClassTree t = (NewClassTree)tree;
            n.put("type", t.getIdentifier().toString()); n.put("args", nodes(t.getArguments()));
            n.put("body", node(t.getClassBody()));
        } else if (tree instanceof NewArrayTree) {
            NewArrayTree t = (NewArrayTree)tree;
            n.put("type", text(t.getType())); n.put("dimensions", nodes(t.getDimensions()));
            n.put("values", t.getInitializers() == null ? null : nodes(t.getInitializers()));
        } else if (tree instanceof ArrayAccessTree) {
            n.put("object", node(((ArrayAccessTree)tree).getExpression()));
            n.put("index", node(((ArrayAccessTree)tree).getIndex()));
        } else if (tree instanceof AssignmentTree) {
            n.put("target", node(((AssignmentTree)tree).getVariable()));
            n.put("value", node(((AssignmentTree)tree).getExpression()));
        } else if (tree instanceof CompoundAssignmentTree) {
            n.put("target", node(((CompoundAssignmentTree)tree).getVariable()));
            n.put("value", node(((CompoundAssignmentTree)tree).getExpression()));
        } else if (tree instanceof BinaryTree) {
            n.put("left", node(((BinaryTree)tree).getLeftOperand()));
            n.put("right", node(((BinaryTree)tree).getRightOperand()));
        } else if (tree instanceof UnaryTree) n.put("expression", node(((UnaryTree)tree).getExpression()));
        else if (tree instanceof ParenthesizedTree) n.put("expression", node(((ParenthesizedTree)tree).getExpression()));
        else if (tree instanceof TypeCastTree) {
            n.put("expression", node(((TypeCastTree)tree).getExpression()));
            n.put("type", ((TypeCastTree)tree).getType().toString());
        }
        else if (tree instanceof ConditionalExpressionTree) {
            ConditionalExpressionTree t = (ConditionalExpressionTree)tree;
            n.put("condition", node(t.getCondition())); n.put("yes", node(t.getTrueExpression())); n.put("no", node(t.getFalseExpression()));
        } else if (tree instanceof IfTree) {
            IfTree t = (IfTree)tree;
            n.put("condition", node(t.getCondition())); n.put("yes", node(t.getThenStatement())); n.put("no", node(t.getElseStatement()));
        } else if (tree instanceof ForLoopTree) {
            ForLoopTree t = (ForLoopTree)tree;
            n.put("init", nodes(t.getInitializer())); n.put("condition", node(t.getCondition()));
            n.put("update", nodes(t.getUpdate())); n.put("body", node(t.getStatement()));
        } else if (tree instanceof EnhancedForLoopTree) {
            EnhancedForLoopTree t = (EnhancedForLoopTree)tree;
            n.put("variable", node(t.getVariable())); n.put("expression", node(t.getExpression())); n.put("body", node(t.getStatement()));
        } else if (tree instanceof WhileLoopTree) {
            n.put("condition", node(((WhileLoopTree)tree).getCondition())); n.put("body", node(((WhileLoopTree)tree).getStatement()));
        } else if (tree instanceof DoWhileLoopTree) {
            n.put("condition", node(((DoWhileLoopTree)tree).getCondition())); n.put("body", node(((DoWhileLoopTree)tree).getStatement()));
        } else if (tree instanceof LambdaExpressionTree) {
            LambdaExpressionTree t = (LambdaExpressionTree)tree;
            n.put("parameters", nodes(t.getParameters())); n.put("body", node(t.getBody()));
        } else if (tree instanceof MemberReferenceTree) {
            n.put("object", node(((MemberReferenceTree)tree).getQualifierExpression()));
            n.put("name", ((MemberReferenceTree)tree).getName().toString());
        } else if (tree instanceof TryTree) {
            TryTree t = (TryTree)tree;
            n.put("body", node(t.getBlock())); n.put("catches", nodes(t.getCatches())); n.put("finally", node(t.getFinallyBlock()));
        } else if (tree instanceof CatchTree) n.put("body", node(((CatchTree)tree).getBlock()));
        else if (tree instanceof SynchronizedTree) n.put("body", node(((SynchronizedTree)tree).getBlock()));
        else if (tree instanceof SwitchTree) {
            n.put("expression", node(((SwitchTree)tree).getExpression())); n.put("cases", nodes(((SwitchTree)tree).getCases()));
        } else if (tree instanceof CaseTree) {
            CaseTree t = (CaseTree)tree;
            n.put("labels", strings(t.getExpressions()));
            n.put("statements", t.getStatements() == null ? null : nodes(t.getStatements())); n.put("body", node(t.getBody()));
        } else n.put("text", tree.toString());
        return n;
    }
    private static String text(Tree tree) { return tree == null ? "" : tree.toString(); }
    private static List<Object> strings(Iterable<? extends Tree> trees) {
        List<Object> result = new ArrayList<>(); for (Tree tree : trees) result.add(tree.toString()); return result;
    }
    private static Map<String,Object> object(Object... items) {
        Map<String,Object> value = new LinkedHashMap<>();
        for (int i=0; i<items.length; i+=2) value.put((String)items[i], items[i+1]);
        return value;
    }
    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map) {
            StringJoiner out = new StringJoiner(",", "{", "}");
            for (Object item : ((Map<?,?>)value).entrySet()) {
                Map.Entry<?,?> entry = (Map.Entry<?,?>)item;
                out.add(json(entry.getKey()) + ":" + json(entry.getValue()));
            }
            return out.toString();
        }
        if (value instanceof Iterable) {
            StringJoiner out = new StringJoiner(",", "[", "]");
            for (Object item : (Iterable<?>)value) out.add(json(item)); return out.toString();
        }
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) {
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default: if (c < 32) out.append(String.format("\\u%04x", (int)c)); else out.append(c);
            }
        }
        return out.append('"').toString();
    }
}

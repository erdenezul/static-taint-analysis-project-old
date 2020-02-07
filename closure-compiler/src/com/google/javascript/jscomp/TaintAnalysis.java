package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;

import java.util.*;

import static com.google.javascript.jscomp.Variable.fixName;


public class TaintAnalysis extends DataFlowAnalysis<Node, TaintDef> {
    private final AbstractCompiler compiler;
    private final Set<Var> escaped;
    private final Map<String, Var> allVarsInFn;
    private final Map<String, Variable> allVarInstancesInFn;
    private TaintDef taintedDef;
    private List<Var> orderedVars;
    private Function function;

    public TaintAnalysis(
            ControlFlowGraph<Node> cfg,
            Scope jsScope,
            AbstractCompiler compiler,
            SyntacticScopeCreator scopeCreator) {
        super(cfg, new TaintDefJoin());
        this.compiler = compiler;
        this.escaped = new HashSet<Var>();
        this.allVarsInFn = new HashMap<>();
        this.allVarInstancesInFn = new HashMap<>();
        this.orderedVars = new ArrayList<Var>();
        this.taintedDef = null;
        this.function = null;

        computeEscaped(jsScope.getParent(), escaped, compiler, scopeCreator);
        NodeUtil.getAllVarsDeclaredInFunction(
                allVarsInFn, orderedVars, compiler, scopeCreator, jsScope.getParent()
        );
        for (String keyVar : allVarsInFn.keySet()) {
            Var value = allVarsInFn.get(keyVar);
            Variable v = new Variable(keyVar, value.getNode().getLineno());
            allVarInstancesInFn.put(Variable.fixName(keyVar), v);
        }
    }

    /**
     * Case 1
     * ======
     * let input = retSource()
     * should return retSource
     */
    public static ArrayList<String> extractNamesFromExpression(Node varNode) {
        ArrayList<String> names = new ArrayList<>();
        Node c = varNode.getFirstChild();
        if (varNode.getLastChild().getLastChild() != null && varNode.getLastChild().getLastChild().isObjectLit())
            return names;
        while (c != null) {
            if (c.isName())
                names.add(Variable.fixName(c.getString()));
            if (c.hasChildren())
                c = c.getFirstChild();
            else
                c = c.getNext();
        }
        return names;
    }

    /**
     * q.length > len && sink(q)
     * AND
     * /    \
     * GT     call
     * /  \
     * sink   q
     * sink(q)
     * CALL
     * /    \
     * sink    q
     * <p>
     * something = retSource()
     * EXPR_RESULT
     * /          \
     * assign      retsource
     * /
     * something
     *
     * @param node The node in question
     */
    public static void extractNamesExpressionResult(Node node, ArrayList<String> names) {
        if (node == null) return;

        if (node.isName()) {
            String varName = Variable.fixName(node.getString());
            if (!names.contains(varName)) {
                names.add(varName);
            }
        }

        extractNamesExpressionResult(node.getFirstChild(), names);
        extractNamesExpressionResult(node.getLastChild(), names);
    }

    @Override
    boolean isForward() {
        return true;
    }

    @Override
    TaintDef flowThrough(Node node, TaintDef input) {
        if (input.getFunction() == null) {
            if (this.taintedDef != null) {
                input.setFunction(this.taintedDef.getFunction());
            }
        }
        // TaintDef output = new TaintDef(input);
        /**
         * in for loop, it will create new scope. Therefore,
         * new initial lattice would initiated and function would be
         * null. To avoid NullPointerException, function set from
         * the TaintAnalysis.
         */
        computeTainted(node, node, input);
        this.taintedDef = input;
        return input;
    }

    @Override
    TaintDef createInitialEstimateLattice() {
        TaintDef est = new TaintDef();
        return est;
    }

    @Override
    TaintDef createEntryLattice() {
        TaintDef allVars = new TaintDef(allVarInstancesInFn);
        return allVars;
    }

    /**
     * let userInfo = {
     * id: id,
     * name: name,
     * }
     * userInfo depends on id, name
     *
     * @param varNode variable node
     * @return [id, name]
     */
    public ArrayList<String> extractNamesFromObjectLit(Node varNode) {
        Node litNode = varNode.getLastChild().getLastChild();
        ArrayList<String> names = new ArrayList<String>();
        if (litNode != null && !litNode.isObjectLit()) return names;
        if (varNode.getFirstChild().isName()) {
            names.add(varNode.getFirstChild().getString());
        }
        for (Node c = litNode.getFirstChild(); c != null; c = c.getNext()) {
            if (c.isStringKey()) {
                Node nameNode = c.getFirstChild();
                if (nameNode.isName()) {
                    names.add(fixName(nameNode.getString()));
                }
            }
        }
        return names;
    }

    /**
     * Detect else condition
     * if true
     * exprResult
     * else:
     * exprResultElse
     *
     * @param exprNode exprResultElse
     * @return
     */
    public boolean isElse(Node exprNode) {
        Node blockNode = exprNode.getParent();
        Node ifNode = blockNode.getParent();
        return ifNode.isIf() && ifNode.getNext() != null && blockNode.getNext() == null;
    }

    /**
     * case1:
     * varNames = [len] <-> len = 20;
     * case2:
     * varNames = [input, retSource]
     * case3:
     * varNames = [q, input]
     * case4
     * varNames = [ans, sink, q]
     * case5
     * varNames = [sink q] isNew=false
     * case6
     * varNames = [final_input, input, next_input]
     * let final_input = next_input + input;
     * case7 varNames = [o push x]
     *
     * @param varNames variable list
     * @param output
     */
    public void handleVar(ArrayList<String> varNames, TaintDef output, Boolean isNew) {
        /**
         * case1
         */
        if (varNames.size() == 0) return;
        if (varNames.size() == 1) {
            output.addVariableDepend(varNames.get(0), "", isNew);
        } else if (varNames.size() == 2) {
            /**
             * case2 and case3
             */
            output.addVariableDepend(varNames.get(0), varNames.get(1), isNew);
            /**
             * case5
             */
            if (varNames.get(0).equals("sink")) {
                output.addVariableDepend(varNames.get(1), varNames.get(0), isNew);
            }
        } else if (varNames.size() == 3 && varNames.indexOf("sink") >= 0) {
            // answer depends on q
            output.addVariableDepend(varNames.get(0), varNames.get(2), isNew);
            // q is reached to rink
            output.addVariableDepend(varNames.get(2), varNames.get(1), isNew);
        } else {
            String rootName = varNames.get(0);
            for (int i = 1; i < varNames.size(); i++) {
                output.addVariableDepend(rootName, varNames.get(i), isNew);
            }
        }
    }

    private void computeTainted(Node n, Node cfgNode, TaintDef output) {
        ArrayList<String> names;
        output.setSourceFilename(n.getSourceFileName());
        switch (n.getToken()) {
            case FUNCTION:
                /**
                 * Initiate function in taintDef with name and line number
                 */
                Function func = new Function(
                        n.getFirstChild().getString(),
                        n.getLineno());
                output.setFunction(func);
                this.function = func;
                return;
            case VAR:
                names = extractNamesFromExpression(n);
                this.handleVar(names, output, true);

                // object list
                names = extractNamesFromObjectLit(n);
                this.handleVar(names, output, true);

                return;
            case FOR:
                return;
            case IF:
            case CASE:
                output.getFunction().setConditional(true);
                return;
            case BREAK:
            case DEFAULT_CASE:
                // default case will always run
                output.getFunction().setConditional(false);
                return;
            case EXPR_RESULT:
                /**
                 * left-hand side operation
                 * q.length > 20 && sink(q)
                 */
                if (n.getFirstChild().isAnd()) {
                    output.getFunction().setConditional(true);
                }
                /**
                 * if condition;
                 *    final = extra_tax - 10;
                 * else:
                 *    final = extra_tax - 10;
                 */
                if (isElse(n)) {
                    output.getFunction().setConditional(false);
                }
                names = new ArrayList<>();
                extractNamesExpressionResult(n, names);
                this.handleVar(names, output, false);
                return;
            case BLOCK:
            case EMPTY:
                return;
            default:
                return;
        }
    }

    public TaintDef getTainted() {
        return this.taintedDef;
    }
}


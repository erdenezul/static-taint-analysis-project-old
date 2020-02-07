package com.google.javascript.jscomp;

import com.google.javascript.jscomp.graph.LatticeElement;

import java.util.HashMap;
import java.util.Map;


public class TaintDef implements LatticeElement {
    private static boolean debug = true;
    private Function function;
    private Map<String, Variable> vars;
    private String sourceFilename;
    private boolean conditional;

    public TaintDef() {
        this.setFunction(null);
        this.setVars(new HashMap<>());
        this.setConditional(false);
    }

    public TaintDef(Map<String, Variable> variables) {
        this();
        this.setVars(variables);
    }

    public TaintDef(TaintDef other) {
        this(other.getVars());
        this.setFunction((other.getFunction()));
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TaintDef)) return false;
        TaintDef otherDef = (TaintDef) other;
        return this.toString().equals(otherDef.toString());
    }

    public void addVariableDepend(String varName, String dependName, Boolean isNew, Boolean conditional) {
        this.getFunction().setConditional(conditional);
        this.addVariableDepend(varName, dependName, isNew);
        this.getFunction().setConditional(!conditional);
    }

    /**
     * input -> retSource -> input variable should be flagged as source
     *
     * @param dependName it could be sink, retSource or another variable name
     */
    public void addVariableDepend(String varName, String dependName, Boolean isNew) {
        varName = Variable.fixName(varName);
        dependName = Variable.fixName(dependName);
        Function func = this.getFunction();
        Variable var = this.getVars().get(varName);
        Variable sourceVar = this.getVars().get(dependName);

        // variable not found
        if (var == null) return;

        // input, retSource
        var.addDependence(dependName);

        // input, q
        if (sourceVar != null) var.addDependVariable(sourceVar);

        // variable condition
        // function.conditional=True and sink(input)
        // input should not be flagged as optional
        if (!dependName.equals("sink")) {
            var.setOptional(func.getConditional());
        }

        // q sink
        if (dependName.equals("sink")) {
            func.noticeReachedSink(var, func.getConditional());
        }
    }

    public Function getFunction() {
        return function;
    }

    public void setFunction(Function function) {
        this.function = function;
    }

    public Map<String, Variable> getVars() {
        return vars;
    }

    public void setVars(Map<String, Variable> vars) {
        this.vars = vars;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public void setSourceFilename(String sourceFilename) {
        this.sourceFilename = sourceFilename;
    }

    public boolean isConditional() {
        return conditional;
    }

    public void setConditional(boolean conditional) {
        this.conditional = conditional;
    }
}

class TaintDefJoin extends JoinOp.BinaryJoinOp<TaintDef> {

    @Override
    TaintDef apply(TaintDef latticeA, TaintDef latticeB) {
        if (latticeA.equals(latticeB)) {
            latticeB.getFunction().setConditional(false);
            return latticeB;
        }
        if (latticeB.getFunction() == null) {
            return latticeA;
        }
        return latticeB;
    }
}

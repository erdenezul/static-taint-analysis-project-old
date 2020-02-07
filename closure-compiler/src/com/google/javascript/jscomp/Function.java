package com.google.javascript.jscomp;

import java.util.HashMap;
import java.util.Map;


public class Function {
    final String name;
    final int lineNumber;
    boolean conditional;
    Map<String, Variable> sourcesMustReaching;
    Map<String, Variable> sourcesMayReaching;
    private Map<String, Variable> variables;

    public Function(String name, int lineNumber) {
        this.name = name;
        this.conditional = false;
        this.lineNumber = lineNumber;
        this.setVariables(new HashMap<String, Variable>());
        this.sourcesMustReaching = new HashMap<>();
        this.sourcesMayReaching = new HashMap<>();
    }

    /**
     * case1:
     * input=retSource()
     * q = bla + input;
     * sink(q)
     * q is reached to sink but q's source var is input.
     * <p>
     * case2:
     * function.conditional = true
     * parentSources: next_input as optional,
     *
     * @param sinkVar
     */
    public void noticeReachedSink(Variable sinkVar, Boolean conditional) {
        Map<String, Variable> parentSources = new HashMap<>();
        sinkVar.getParentSourceVariables(parentSources);
        for (Variable var : parentSources.values()) {
            if (conditional) {
                // case sink(input) input.optional=false
                // may reach
                if (var.getOptional()) {
                    //conditional and defined as optional
                    // must also be may
                    this.sourcesMustReaching.put(var.getName(), var);
                }
                this.sourcesMayReaching.put(var.getName(), var);
            } else {
                // must also be may
                this.sourcesMustReaching.put(var.getName(), var);
                this.sourcesMayReaching.put(var.getName(), var);
            }
        }
    }

    public boolean getConditional() {
        return this.conditional;
    }

    public void setConditional(Boolean conditional) {
        this.conditional = conditional;
    }

    /**
     * Returns function full name
     *
     * @return "getInformation@1"
     */
    public String getFunctionName() {
        return String.format("%s@%d", this.name, this.lineNumber);
    }

    /**
     * Prettified json output
     */
    @Override
    public String toString() {
        String output = String.format(
                "\"%s\":{\n" +
                        "\"sources_that_must_reach_sinks\": %s,\n" +
                        "\"sources_that_may_reach_sinks\": %s\n}",
                this.getFunctionName(), this.sourcesMustReaching.values(), this.sourcesMayReaching.values());

        return output;
    }

    @Override
    public boolean equals(Object other) {
        return (other instanceof Function) && this.toString().equals(((Function) other).toString());
    }

    public Map<String, Variable> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Variable> variables) {
        this.variables = variables;
    }
}

class Path {

    public Path() {

    }

    public Boolean isMustReachable() {
        return true;
    }

    public Boolean isMayReachable() {
        return true;
    }

}


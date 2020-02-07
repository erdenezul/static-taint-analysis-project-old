package com.google.javascript.jscomp;


import java.util.*;

public class Variable {

    private String name;
    private int lineNumber;
    private Map<String, Variable> dependVariables;
    private Boolean isSource;
    private Boolean isSink;
    // which means variable defined in condition.
    private Boolean optional;
    private Boolean inElse;

    public Variable() {
        this.isSource = false;
        this.isSink = false;
        this.optional = false;
        this.dependVariables = new HashMap<>();
        this.inElse = false;
    }

    public Variable(String name) {
        this();
        this.setName(fixName(name));
    }

    public Variable(String name, int lineNo) {
        this(name);
        this.setLineNumber(lineNo);
    }

    /**
     * income_tax(source) - > extra_tax -> final
     * final reached sink then extra_tax
     * final sourceVars = extra_tax + extra_tax.sourceVars
     *
     * @param var
     */
    public void addDependVariable(Variable var) {
        if (var == null) return;
        this.dependVariables.put(var.getName(), var);
        for (Variable headOfVar : var.getDependVariables()) {
            this.dependVariables.put(headOfVar.getName(), headOfVar);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "%s@%d", this.getName(),
                this.getLineNumber());
    }

    /**
     * @param dependName value would retSource or sink
     */
    public void addDependence(String dependName) {
        if (dependName.equals("retSource")) {
            this.setSource(true);
        } else if (dependName.equals("sink")) {
            this.setSink(true);
        }
    }

    /**
     * When variable name conflicts with javascript keyword
     * analyizer adds $jscomp$ into variable name which do not
     * need me.
     * So fixName needed.
     */
    public static String fixName(String name) {
        int index = name.indexOf("$jscomp$");
        if (index > 0) {
            return name.substring(0, index);
        }
        return name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return income_tax -> extra -> final
     * final.getSourceVar
     */
    public Collection<Variable> getDependVariables() {
        return dependVariables.values();
    }

    /**
     * Recursively finds all parent source variables
     *
     * @param parentContainer list container for variables
     */
    public void getParentSourceVariables(Map<String, Variable> parentContainer) {
        if (this.getDependVariables().size() == 0) {
            if (this.getSource()) {
                parentContainer.put(this.getName(), this);
            }
            return;
        } else for (Variable parentVar : this.dependVariables.values()) {
            parentVar.getParentSourceVariables(parentContainer);
            if (parentVar.getSource()) {
                parentContainer.put(this.getName(), parentVar);
            }
        }
        return;
    }

    public Boolean getSource() {
        return isSource;
    }

    public void setSource(Boolean source) {
        isSource = source;
    }

    public Boolean getSink() {
        return isSink;
    }

    public void setSink(Boolean sink) {
        isSink = sink;
    }

    public Boolean getOptional() {
        return optional;
    }

    public void setOptional(Boolean optional) {
        this.optional = optional;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }
}


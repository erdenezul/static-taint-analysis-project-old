package com.google.javascript.jscomp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.javascript.jscomp.*;
import com.google.javascript.rhino.Node;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.Buffer;
import java.util.ArrayList;

public class TaintPass implements CompilerPass, NodeTraversal.ScopedCallback {
    private final AbstractCompiler compiler;

    private ControlFlowGraph<Node> cfg;
    private ArrayList<TaintDef> tainted;

    public TaintPass(AbstractCompiler compiler) {
        this.compiler = compiler;
        this.tainted = new ArrayList<>();
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
        return !n.isScript() || !t.getInput().isExtern();
    }
    @Override
    public void enterScope(NodeTraversal t) {
        if (t.inGlobalScope()) {
            return;
        }

        if (!t.getScope().isFunctionBlockScope()) {
            return;
        }
        SyntacticScopeCreator scopeCreator = (SyntacticScopeCreator) t.getScopeCreator();
        Node functionScopeRoot = t.getScopeRoot().getParent();
        ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, false, true);
        cfa.process(null, functionScopeRoot);
        cfg = cfa.getCfg();

        TaintAnalysis taintAnalysis = new TaintAnalysis(cfg, t.getScope(), compiler, scopeCreator);
        taintAnalysis.analyze();
        this.tainted.add(taintAnalysis.getTainted());
    }

    @Override
    /**
     * Processed all functions and should result to file in here.
     */
    public void process(Node externs, Node root) {
        (new NodeTraversal(compiler, this, new SyntacticScopeCreator(compiler)))
                .traverseRoots(externs, root);
        try {
            this.outputResult();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {

    }

    @Override
    public void exitScope(NodeTraversal t) {
    }

    public void outputResult() throws IOException {
        String sourceFilename = this.tainted.get(0).getSourceFilename();
        String filePrefix = sourceFilename.split(".js")[0];
        String fileName = filePrefix + "_out.json";
        BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));
        String output = "{\n";
        for (int i = 0; i < this.tainted.size(); i++) {
            TaintDef taintOutput = this.tainted.get(i);
            output += taintOutput.getFunction().toString();
            if (i != this.tainted.size() - 1) {
                output += ",\n";
            }
        }
        output += "\n}";
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonParser jp = new JsonParser();
        JsonElement je = jp.parse(output);
        writer.write(gson.toJson(je));
        writer.close();
    }

}
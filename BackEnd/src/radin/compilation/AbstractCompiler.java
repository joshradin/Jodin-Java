package radin.compilation;

import radin.compilation.tags.AbstractCompilationTag;
import radin.interphase.ICompilationSettings;
import radin.interphase.lexical.Token;
import radin.typeanalysis.TypeAugmentedSemanticNode;

import java.io.PrintWriter;
import java.util.Locale;

public abstract class AbstractCompiler {

    private PrintWriter printWriter;
    private static ICompilationSettings settings;
    
    public ICompilationSettings getSettings() {
        return settings;
    }
    
    public static void setSettings(ICompilationSettings settings) {
        AbstractCompiler.settings = settings;
    }
    
    public AbstractCompiler(PrintWriter printWriter) {
        this.printWriter = printWriter;
    }
    
    abstract public boolean compile(TypeAugmentedSemanticNode node);
    
    
    public void flush() {
        printWriter.flush();
    }
    
    public void close() {
        printWriter.close();
    }
    
    
    public void print(String s) {
        printWriter.print(s);
    }
    
    public void print(Token s){
        printWriter.print(s.getImage());
    }
    
    public void print(Object obj) {
        printWriter.print(obj);
    }
    
    public void println() {
        printWriter.println();
    }
    
    public void println(String x) {
        printWriter.println(x);
    }
    
    public void println(Object x) {
        printWriter.println(x);
    }
    
    public PrintWriter getPrintWriter() {
        return printWriter;
    }
    
    public PrintWriter printf(String format, Object... args) {
        return printWriter.printf(format, args);
    }
    
    public PrintWriter printf(Locale l, String format, Object... args) {
        return printWriter.printf(l, format, args);
    }
}

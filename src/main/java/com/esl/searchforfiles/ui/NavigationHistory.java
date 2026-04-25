package com.esl.searchforfiles.ui;


import java.util.ArrayDeque;
import java.util.Deque;

// 1. NavigationHistory — pilha de histórico de navegação
//    Classe utilitária simples, crie como arquivo separado
// ════════════════════════════════════════════════════════════════
public class NavigationHistory {

    private final Deque<String> backStack  = new ArrayDeque<>();
    private final Deque<String> forwardStack = new ArrayDeque<>();
    private String current = null;

    /** Navega para um novo caminho, limpando o forward stack. */
    public void push(String path) {
        if (path == null || path.equals(current)) return;
        if (current != null) backStack.push(current);
        forwardStack.clear();
        current = path;
    }

    /** Volta uma posição. Retorna o caminho anterior, ou null se não houver. */
    public String back() {
        if (backStack.isEmpty()) return null;
        forwardStack.push(current);
        current = backStack.pop();
        return current;
    }

    /** Avança uma posição. Retorna o próximo caminho, ou null se não houver. */
    public String forward() {
        if (forwardStack.isEmpty()) return null;
        backStack.push(current);
        current = forwardStack.pop();
        return current;
    }

    public boolean canGoBack()    { return !backStack.isEmpty(); }
    public boolean canGoForward() { return !forwardStack.isEmpty(); }
    public String  getCurrent()   { return current; }
}

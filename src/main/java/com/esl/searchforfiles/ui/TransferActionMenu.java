package com.esl.searchforfiles.ui;


import javax.swing.*;
import java.awt.*;

public class TransferActionMenu extends JPopupMenu {

    public interface ActionListener {
        void onCopy();
        void onMove();
        void onDelete();
    }

    public TransferActionMenu(ActionListener listener) {
        JMenuItem copyItem = new JMenuItem("📋  Copiar");
        copyItem.setFont(new Font("SansSerif", Font.PLAIN, 14));
        copyItem.addActionListener(e -> listener.onCopy());
        add(copyItem);

        JMenuItem moveItem = new JMenuItem("✂️  Mover");
        moveItem.setFont(new Font("SansSerif", Font.PLAIN, 14));
        moveItem.addActionListener(e -> listener.onMove());
        add(moveItem);

        addSeparator();

        JMenuItem deleteItem = new JMenuItem("🗑️  Apagar");
        deleteItem.setFont(new Font("SansSerif", Font.PLAIN, 14));
        deleteItem.addActionListener(e -> listener.onDelete());
        add(deleteItem);
    }
}
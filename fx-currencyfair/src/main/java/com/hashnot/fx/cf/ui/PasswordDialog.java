package com.hashnot.fx.cf.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.ExecutionException;

/**
 * @author Rafał Krupiński
 */
public class PasswordDialog extends JDialog {


    public static void main(String[] args) throws ExecutionException, InterruptedException {
        PasswordFuture listener = new PasswordFuture();
        new PasswordDialog(listener).setVisible(true);
        System.out.print(listener.get());
    }

    public PasswordDialog(ValueListener<String> listener) {
        setLayout(new BoxLayout(getContentPane(), BoxLayout.X_AXIS));
        JTextField pass = new JTextField();
        pass.setColumns(20);
        add(pass);
        JButton ok = new JButton("OK");
        ok.addActionListener(new OkActionListener(pass, this, listener));
        add(ok);
        pack();
    }

    private static class OkActionListener implements ActionListener {
        private JTextField pass;
        private Window window;
        private ValueListener<String> listener;

        public OkActionListener(JTextField pass, Window window, ValueListener<String> listener) {
            this.pass = pass;
            this.window = window;
            this.listener = listener;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            listener.setValue(pass.getText());
            window.setVisible(false);
            window.dispose();
        }
    }

    static interface ValueListener<T> {
        void setValue(T value);
    }
}

package main;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;

public class MainWindow {

    public MainWindow(MainPanel mainPanel) {

        JFrame jframe = new JFrame();

        jframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jframe.add(mainPanel);
        jframe.setLocationRelativeTo(null);
        jframe.setResizable(true);
        jframe.setName("Main Window");
        jframe.setIconImage(Toolkit.getDefaultToolkit().getImage(MainWindow.class.getResource("Icon.png")));
        jframe.pack();
        jframe.setVisible(true);

        jframe.addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {

            }

            @Override
            public void windowLostFocus(WindowEvent e) {
                mainPanel.getLevel().windowFocusLost();
            }
        });

    }
}

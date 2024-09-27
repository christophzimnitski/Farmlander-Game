package main;

import javax.swing.*;
import java.awt.*;
import static java.awt.Component.*;
import static java.awt.Frame.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;

public class MainWindow {
    JFrame jframe = new JFrame();
    GraphicsEnvironment graphics = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice device = graphics.getDefaultScreenDevice();
    
    public MainWindow(MainPanel mainPanel) {

        jframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jframe.add(mainPanel);
        jframe.setLocation(-10,0);
        jframe.setResizable(true);
        jframe.setTitle("Farmlander");
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
    
    public void setFullscreen() {
        if (jframe.getExtendedState() == MAXIMIZED_BOTH) {
            if(device.getFullScreenWindow() == null) device.setFullScreenWindow(jframe);
        }
    }
    public void unSetFullscreen() {
        device.setFullScreenWindow(null);
        jframe.setLocation(-10, 0);
    }
}

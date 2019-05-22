/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package api;

import java.time.LocalTime;

/**
 *
 * @author Administrator
 */
public class ChinaIdeaTimeCompute implements Runnable{
       
    @Override public void run() {
        try {
            System.out.println("Idea Compute" + LocalTime.now());
            //IdeaProcessorJolt.chooseGraphs();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("interrupted");
        } 
    }
    
}

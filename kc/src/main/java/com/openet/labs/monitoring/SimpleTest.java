package com.openet.labs.monitoring;

import java.io.IOException;
import java.util.function.BiConsumer;

public class SimpleTest {
    public static void main(String[] args) {
//        System.out.println("");
//        System.out.println(System.getenv());
//        for (int i = 0; i < System.getenv().keySet().size(); i++) {
//            System.out.println(System.getenv().values());
//        }
//        for (Object var :
//                System.getenv().values()) {
//            System.out.println(var);
//        }
//        for (Object o :
//                System.getenv().forEach(new );) {
//
//        }
        System.getenv().forEach(new BiConsumer<String, String>() {
            @Override
            public void accept(String s, String s2) {
//                System.out.println("s: " + s + " || " +s2);
                try {
//                    ProcessBuilder processBuilder = new ProcessBuilder("ls", "-l");
//                    processBuilder.start()
                    Process process = new ProcessBuilder().command("ls").start();
                    System.out.println(process.waitFor());
                    System.out.println(process.getErrorStream());
                    System.out.println(process.getInputStream());
                    System.out.println(process.exitValue());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}

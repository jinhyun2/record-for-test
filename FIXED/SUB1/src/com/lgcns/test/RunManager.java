package com.lgcns.test;

import com.lgcns.test.util.SimpleQueue;

import java.util.Scanner;

public class RunManager {

    public static void main(String[] args) {
        SimpleQueue queue = new SimpleQueue();

        try (Scanner sc = new Scanner(System.in)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();

                if (line.startsWith("SEND ")) {
                    queue.send(line.substring(5));
                } else if (line.equals("RECEIVE")) {
                    String msg = queue.receive();
                    if (msg != null) {
                        System.out.println(msg);
                    }
                }
            }
        }
    }
}

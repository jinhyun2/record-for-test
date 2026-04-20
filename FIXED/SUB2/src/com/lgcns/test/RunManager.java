package com.lgcns.test;

import com.lgcns.test.util.NamedQueueManager;

import java.util.Scanner;

public class RunManager {

    public static void main(String[] args) {
        NamedQueueManager manager = new NamedQueueManager();

        try (Scanner sc = new Scanner(System.in)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();

                if (line.startsWith("SEND ")) {
                    // split(" ", 3): 메시지에 공백이 포함되어도 정확히 분리
                    String[] parts = line.split(" ", 3);
                    if (parts.length < 3) continue;
                    if (manager.send(parts[1], parts[2]) == NamedQueueManager.Result.QUEUE_FULL) {
                        System.out.println("Queue Full");
                    }

                } else if (line.startsWith("RECEIVE ")) {
                    String[] parts = line.split(" ", 2);
                    if (parts.length < 2) continue;
                    String msg = manager.receive(parts[1]);
                    if (msg != null) System.out.println(msg);

                } else if (line.startsWith("CREATE ")) {
                    String[] parts = line.split(" ", 3);
                    if (parts.length < 3) continue;
                    try {
                        if (manager.create(parts[1], Integer.parseInt(parts[2].trim()))
                                == NamedQueueManager.Result.QUEUE_EXIST) {
                            System.out.println("Queue Exist");
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid capacity: " + parts[2]);
                    }
                }
            }
        }
    }
}

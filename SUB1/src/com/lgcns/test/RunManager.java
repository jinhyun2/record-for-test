package com.lgcns.test;

import java.util.Scanner;
import java.util.concurrent.LinkedBlockingQueue;

public class RunManager {

	public static void main(String[] args) {
		LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
		Scanner sc = new Scanner(System.in);
		String line;

		while((line=sc.nextLine()) != null) {
			if (line.startsWith("SEND ")) {
				String msg = line.substring(line.indexOf(" ") + 1);
				queue.add(msg);
			} else if (line.equals("RECEIVE")) {
				if (!queue.isEmpty()) {
					System.out.println(queue.poll());
				}
			}
		}
	}

}

package com.lgcns.test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class RunManager {

	public static void main(String[] args) {
		Map<String, LinkedBlockingQueue<String>> queueMap = new ConcurrentHashMap<>();
		Scanner sc = new Scanner(System.in);
		String line;

		while((line=sc.nextLine()) != null) {
			if (line.startsWith("SEND ")) {
				String[] smsgs = line.split(" ");
				LinkedBlockingQueue<String> queue = queueMap.get(smsgs[1]);
				if (queue != null && queue.remainingCapacity() > 0) {
					queue.add(smsgs[2]);
				} else if (queue != null && queue.remainingCapacity() == 0){
					System.out.println("Queue Full");
				}
			} else if (line.startsWith("RECEIVE ")) {
				String[] rmsgs = line.split(" ");
				LinkedBlockingQueue<String> queue = queueMap.get(rmsgs[1]);
				if (queue != null && !queue.isEmpty()) {
					System.out.println(queue.poll());
				}
			} else if (line.startsWith("CREATE ")) {
				String[] cmsgs = line.split(" ");
				Queue<String> queue = queueMap.get(cmsgs[1]);
				if (queue != null) {
					System.out.println("Queue Exist");
				} else {
					queueMap.put(cmsgs[1], new LinkedBlockingQueue<>(Integer.valueOf(cmsgs[2])));
				}
			}
		}
	}

}

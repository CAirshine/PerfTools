package perf;

import java.util.ArrayList;

public class Test {

	public static void main(String[] args) throws Exception {
		
		ArrayList<Long> strings = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			Thread.sleep(10);
			long now = System.currentTimeMillis();
			strings.add(now);
		}
		
		for (int i = 0; i < strings.size(); i++) {
			System.out.println(strings.get(i));
		}
	}
}

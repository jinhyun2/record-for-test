package test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class MyJson {

	public static void main(String[] args) {
		JsonElement jsonElement = JsonParser.parseString("{ \"key\":\"value\" }");
		System.out.println(jsonElement.toString());
	}
}

package jp.ats.relay;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class U {

	private static final ThreadLocal<Set<Container>> cycleCheckerThreadLocal = new ThreadLocal<Set<Container>>();

	public static final int BUFFER_SIZE = 1024 * 100;

	public static boolean presents(String value) {
		return value != null && !value.equals("");
	}

	public static String care(String target) {
		return target == null ? "" : target;
	}

	public static String trim(String target) {
		return target == null ? "" : target.trim();
	}

	public static String removeWhiteSpaces(String target) {
		return U.care(target).replaceAll("^\\s+|\\s+$", "");
	}

	public static int getNewLineChoppedLength(byte[] buffer, int lastIndex) {
		byte b;
		while (lastIndex > 0 && ((b = buffer[lastIndex - 1]) == '\n' || b == '\r'))
			lastIndex--;

		return lastIndex;
	}

	/**
	 * デバッグ用の、簡易文字列化メソッドです。
	 * <br>
	 * リフレクションを利用して、内部のフィールド値を出力します。
	 * <br>
	 * 循環参照が発生している場合、二度目の出力時に {repetition} と出力されます。
	 * <br>
	 * 使用上の注意点：
	 * <br>
	 * このメソッドを使用するのはあくまでも用途をデバッグに限定してください。
	 * <br>
	 * また、{@link Object} 以外の親クラスを持つクラスでは、親クラスの toString() メソッドをオーバーライドする可能性があるので、このメソッドを呼ぶ toString() を定義しないほうが無難です。
	 *
	 * @param object 文字列化対象
	 * @return object の文字列表現
	 */
	public static String toString(Object object) {
		Map<String, Object> map = new TreeMap<>();

		boolean top = false;
		Set<Container> checker = cycleCheckerThreadLocal.get();
		if (checker == null) {
			checker = new HashSet<>();
			checker.add(new Container(object));
			cycleCheckerThreadLocal.set(checker);
			top = true;
		}

		try {
			getFields(object.getClass(), object, map, checker);
			return "{id:" + System.identityHashCode(object) + " " + map.toString() + "}";
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		} finally {
			if (top) cycleCheckerThreadLocal.set(null);
		}
	}

	/**
	 * in から読み込めるだけ読み込み、out へ出力します。
	 * @param in 
	 * @param out 
	 * @throws IOException 
	 */
	public static void sendBytes(InputStream in, OutputStream out)
		throws IOException {
		byte[] b = new byte[BUFFER_SIZE];
		int readed;
		while ((readed = in.read(b, 0, BUFFER_SIZE)) > 0) {
			out.write(b, 0, readed);
		}

		out.flush();
	}

	public static <T> T getInstance(String className) {
		try {
			Class<?> clazz = Class.forName(className);

			@SuppressWarnings("unchecked")
			T instance = (T) clazz.newInstance();

			return instance;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static <T> T getInstance(String className, Config config) {
		try {
			Class<?> clazz = Class.forName(className);

			Constructor<?> constructor;
			try {
				constructor = clazz.getConstructor(Config.class);
			} catch (NoSuchMethodException e) {
				//Configがパラメータのコンストラクタがない場合、パラメータなしのコンストラクタを使用する
				@SuppressWarnings("unchecked")
				T instance = (T) clazz.newInstance();
				return instance;
			}

			@SuppressWarnings("unchecked")
			T instance = (T) constructor.newInstance(config);

			return instance;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static InputStream wrap(InputStream input) {
		if (!(input instanceof BufferedInputStream) || !(input instanceof ByteArrayInputStream)) return new BufferedInputStream(input);
		return input;
	}

	private static void getFields(
		Class<?> clazz,
		Object object,
		Map<String, Object> map,
		Set<Container> checker)
		throws IllegalAccessException {
		Class<?> superclass = clazz.getSuperclass();
		if (superclass != null) getFields(superclass, object, map, checker);
		Field[] fields = clazz.getDeclaredFields();
		for (Field field : fields) {
			if (Modifier.isStatic(field.getModifiers())) continue;
			field.setAccessible(true);
			Object value = field.get(object);
			//循環参照を避けるため、一度調査したオブジェクトは使用しない
			if (value != null) {
				Container container = new Container(value);
				if (checker.contains(container)) {
					map.put(field.getName(), "{repetition}");
					continue;
				}
				checker.add(container);
			}

			map.put(field.getName(), value);
		}
	}

	private static class Container {

		private final Object value;

		private Container(Object value) {
			this.value = value;
		}

		@Override
		public boolean equals(Object o) {
			return value == ((Container) o).value;
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(value);
		}
	}
}

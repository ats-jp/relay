package jp.ats.relay;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Command {

	private static final Pattern schemaPattern = Pattern.compile("^jar:file:/(.+)$");

	private static final Pattern jarNamePattern = Pattern.compile("[^/\\\\]+\\.jar");

	private static final Path homePath;

	private static final int EXIT_STATUS_SUCCESS = 0;

	private static final int EXIT_STATUS_FAIL = 1;

	static {
		String myLocation = "/" + Command.class.getName().replaceAll("\\.", "/") + ".class";
		String myPath = Command.class.getResource(myLocation).toString();

		Matcher schemaMatcher = schemaPattern.matcher(myPath);
		if (!schemaMatcher.matches()) throw new IllegalStateException();

		String filePath = schemaMatcher.group(1);

		Matcher jarNameMatcher = jarNamePattern.matcher(filePath);
		if (!jarNameMatcher.find()) throw new IllegalStateException();

		String jarName = jarNameMatcher.group();

		try {
			String myHomePath = URLDecoder.decode(
				filePath.substring(0, filePath.indexOf(jarName + "!" + myLocation)),
				"UTF-8");
			// Windows UNC パス名
			String uncPath = '/' + myHomePath;
			if (Files.exists(Paths.get(myHomePath))) {
				homePath = Paths.get(myHomePath);
			} else if (Files.exists(Paths.get(uncPath))) {
				homePath = Paths.get(uncPath);
			} else {
				throw new IllegalStateException();
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * 第一パラメータを{@link ShellClient}の実装クラス名とし、以降のパラメータを{@link Shell}で使用するパラメータとして実行します。
	 * @param args
	 */
	public static void main(String[] args) {
		ShellClient client;
		try {
			client = (ShellClient) Class.forName(args[0]).getConstructor().newInstance();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(EXIT_STATUS_FAIL);
			return;
		}

		//配列の先頭を除去
		String[] shellArgs = new String[args.length - 1];
		System.arraycopy(args, 1, shellArgs, 0, shellArgs.length);

		Shell.dispatch(shellArgs, new FileSystemResourceManager(homePath), client);

		System.exit(EXIT_STATUS_SUCCESS);
	}
}

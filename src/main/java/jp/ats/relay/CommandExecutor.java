package jp.ats.relay;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

public abstract class CommandExecutor {

	private static final Charset DEFAULT = Charset.defaultCharset();

	/**
	 * コマンド、パラメータ付きコマンド、パラメータ、複数パラメータ等、すべてをスペースで分割し、配列化する
	 * @param command
	 * @return 実行可能な分割済みコマンド用文字列
	 */
	public static String[] prepareCommand(String... command) {
		List<String> list = new LinkedList<>();
		for (String element : command) {
			list.addAll(Arrays.asList(U.trim(element).split(" +")));
		}

		return list.toArray(new String[list.size()]);
	}

	public static CommandExecutor getInstance() {
		return getInstance(Shell.config());
	}

	public static CommandExecutor getInstance(Config config) {
		return U.getInstance(config.getCommandExecutorClass());
	}

	/**
	 * 外部コマンドを実行する
	 * @param command コマンドとパラメータ
	 * @return 外部コマンドの標準出力
	 * @throws InterruptedException
	 */
	public List<String> execute(String... command) throws InterruptedException {
		return execute(b -> b.command(prepareCommand(command)), null, DEFAULT);
	}

	/**
	 * 外部コマンドを実行する
	 * @param charset 出力の文字コード
	 * @param command コマンドとパラメータ
	 * @return 外部コマンドの標準出力
	 * @throws InterruptedException
	 */
	public List<String> execute(Charset charset, String... command) throws InterruptedException {
		return execute(b -> b.command(prepareCommand(command)), null, charset);
	}

	/**
	 * 外部コマンドを実行する
	 * @param in null可 外部コマンドに渡す
	 * @param command コマンドとパラメータ
	 * @return 外部コマンドの標準出力
	 * @throws InterruptedException
	 */
	public List<String> execute(InputStream in, String... command) throws InterruptedException {
		return execute(b -> b.command(prepareCommand(command)), in, DEFAULT);
	}

	/**
	 * 外部コマンドを実行する
	 * @param configFunction ProcessBuilderに設定をするラムダ式
	 * @param in null可 外部コマンドに渡す
	 * @param charset 出力の文字コード
	 * @return 外部コマンドの標準出力
	 * @throws InterruptedException
	 */
	public abstract List<String> execute(
		Consumer<ProcessBuilder> configFunction,
		InputStream in,
		Charset charset)
		throws InterruptedException;
}

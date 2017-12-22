package jp.ats.relay;

import java.io.IOException;

public class NextCommandImpl implements NextCommand {

	/**
	 * 外部コマンドを実行する<br>
	 * このメソッドは、起動した外部コマンドの終了を待たず復帰する
	 * @param command コマンド
	 */
	@Override
	public void execute(String command) {
		ProcessBuilder processBuilder = new ProcessBuilder();
		processBuilder.command(CommandExecutor.prepareCommand(command));

		try {
			processBuilder.start();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}

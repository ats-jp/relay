package jp.ats.relay;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 次工程のコマンドを管理するクラス
 */
public interface NextCommand {

	/**
	 * ロックを確認し、起動可能かどうか判断する
	 * @param lockDir ロックディレクトリ
	 * @return 起動可能かどうか
	 */
	default boolean canExecute(Path lockDir) {
		return Files.notExists(lockDir);
	}

	/**
	 * 次工程を起動する
	 * @param command 次工程
	 */
	void execute(String command);

	/**
	 * 設定から実装クラスを特定しインスタンス化する
	 * @return 設定に定義された実装クラス
	 */
	static NextCommand getInstance() {
		return Shell.newInstance(Shell.config().getNextCommandClass());
	}
}

package jp.ats.relay;

import java.nio.file.Path;

import org.blendee.jdbc.BTransaction;

public abstract class DatabaseQueueProcess extends QueueProcess {

	@Override
	public boolean usesDatabase() {
		return true;
	}

	@Override
	protected Path process(Path file) {
		BTransaction transaction = Shell.transaction();
		try {
			Path result = processInternal(file);
			//一件処理するごとにcommit
			transaction.commit();
			return result;
		} catch (Throwable t) {
			//エラー発生時はここでロールバック
			transaction.rollback();
			throw t;
		}
	}

	protected abstract Path processInternal(Path file);
}

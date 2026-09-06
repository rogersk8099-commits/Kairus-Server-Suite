package gg.neonnexus.smpplatform.phase3.support;

import gg.neonnexus.smpplatform.phase3.common.TransactionRunner;
import java.util.function.Supplier;

public final class DirectTransactionRunner implements TransactionRunner {
    @Override public <T> T required(Supplier<T> work) { return work.get(); }
}

package net.sf.jasperreports.engine.fill;

public abstract class ThreadPoolSubreportRunnerFactory {
    public abstract JRSubreportRunner createSubreportRunner(JRFillSubreport fillSubreport, JRBaseFiller subreportFiller);
}

package org.wso2.carbon.ui.taglibs;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.BodyTagSupport;
import java.io.IOException;


public class ReportNew extends BodyTagSupport {
    private String component;
    private String template;
    private boolean pdfReport;
    private boolean htmlReport;
    private boolean excelReport;
    private String reportDataSession;
    private String jsFunction;

    public String getJsFunction() {
        return jsFunction;
    }

    public void setJsFunction(String jsFunction) {
        this.jsFunction = jsFunction;
    }

    public String getReportDataSession() {
        return reportDataSession;
    }

    public void setReportDataSession(String reportDataSession) {
        this.reportDataSession = reportDataSession;
    }


    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public boolean isPdfReport() {
        return pdfReport;
    }

    public void setPdfReport(boolean pdfReport) {
        this.pdfReport = pdfReport;
    }

    public boolean isHtmlReport() {
        return htmlReport;
    }

    public void setHtmlReport(boolean htmlReport) {
        this.htmlReport = htmlReport;
    }

    public boolean isExcelReport() {
        return excelReport;
    }

    public void setExcelReport(boolean excelReport) {
        this.excelReport = excelReport;
    }


    public int doStartTag() throws JspException {
        JspWriter writer = pageContext.getOut();

        String context = "<script type=\"text/javascript\">" +
                "function generateReportData() {\n" +
                "var data =" + jsFunction + ";\n";
        if (pdfReport) {
            context += "window.open(\"../report?reportDataSession=" + reportDataSession + "&component=" + component + "&template=" + template + "&type=pdf" + "\" ,\"_blank\");\n";
        } else if (htmlReport) {
            context += "window.open(\"../report?reportDataSession=" + reportDataSession + "&component=" + component + "&template=" + template + "&type=html" + "\", ,\"_blank\");\n";

        } else {
            context += "window.open(\"../report?reportDataSession=" + reportDataSession + "&component=" + component + "&template=" + template + "&type=excel" + "\", ,\"_blank\");\n";
        }

        context += "}" + "</script>";

        context += "<div style='float:right;padding-bottom:5px;padding-right:15px;'>;";

        if (pdfReport) {
            context = context + "<a  class='icon-link' style='background-image:url(../admin/images/pdficon.gif);' href=\"javascript:generateReportData()\">Generate Pdf Report</a>";
        }
        if (htmlReport) {
            context = context + "<a  class='icon-link' style='background-image:url(../admin/images/htmlicon.gif);' href=\"javascript:generateReportData()\">Generate Html Report</a>";

        }
        if (excelReport) {
            context = context + "<a  class='icon-link' style='background-image:url(../admin/images/excelicon.gif);' href=\"href=\"javascript:generateReportData()\">Generate Excel Report</a>";

        }
        context = context + "</div><div style='clear:both;'></div>";

        try {
            writer.write(context);
        } catch (IOException e) {
            String msg = "Cannot write reporting tag content";

            throw new JspException(msg, e);
        }
        return EVAL_PAGE;


    }
}

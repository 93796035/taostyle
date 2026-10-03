package com.stgo.taostyle.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stgo.taostyle.domain.Person;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaoDebug {
    private static final Logger logger = LoggerFactory.getLogger(TaoDebug.class);
    private static boolean debugFlag;

    @Autowired
    private TaoUtil taoUtil;
    public static boolean getDebugFlag() {
        return debugFlag;
    }

    public static void setDebugFlag(
            HttpSession session,
            String flag,
            Person person) {
        debugFlag = CC.TRUE.equalsIgnoreCase(flag);
        if (debugFlag) {
            StringBuilder tsb = getSB(session);
            tsb.append("-------------Start to debug for ");
            tsb.append(person.getName());
            tsb.append("-------------");
            tsb.append("<br>");
            info(tsb);
        }
    }

    public static StringBuilder getSB(
            HttpSession session) {
        Object sb = session.getAttribute(CC.SB);
        if (sb == null) {
            sb = new StringBuilder();
            session.setAttribute(CC.SB, sb);
        }
        return (StringBuilder) sb;
    }

    public static void resetSB(
            HttpSession session) {
        session.setAttribute(CC.SB, new StringBuilder());
    }

    public static void error(
            String format,
            Object... arguments) {
        logger.error(format, arguments);
        // if (debugFlag) {
        // StringBuilder sb = new StringBuilder();
        // sb.append("<br>!!!Error!!!");
        // info(sb, format, arguments);
        // }
        StringBuilder sbForEmail = new StringBuilder();
        for (Object argument : arguments) {
            sbForEmail.append(argument.toString());
            sbForEmail.append("\n");
        }
        TaoEmail.sendMessage("issueReport@ShareTheGoodOnes.com", format, "info@ShareTheGoodOnes.com",
                sbForEmail.toString(), null);
    }

    public static void error(
            StringBuilder sb,
            String format,
            Object... arguments) {
        logger.error(format, arguments);
        if (debugFlag) {
            sb.append("<br>!!!Error!!!");
            info(sb, format, arguments);
        }
        StringBuilder sbForEmail = new StringBuilder();
        for (Object argument : arguments) {
            sbForEmail.append(argument.toString());
            sbForEmail.append("\n");
        }
        TaoEmail.sendMessage("issueReport@ShareTheGoodOnes.com", format, "info@ShareTheGoodOnes.com",
                sbForEmail.toString(), null);
    }

    public static void warn(
            StringBuilder sb,
            String format,
            Object... arguments) {
        logger.warn(format, arguments);
        if (debugFlag) {
            sb.append("!!WARN!!");
            info(sb, format, arguments);
        }
    }

    public void info(
            HttpServletRequest request,
            String template,
            Object... args) {
        if (debugFlag) {
            Person person = taoUtil.getCurPerson(request);
            String url = request.getRequestURL().toString();
            String queryString = request.getQueryString();
            if (StringUtils.isNotBlank(queryString)) {
                url = url + "?" + queryString;
            }
            Object menuIdx = request.getSession().getAttribute(CC.menuIdx);
            info(getSB(request.getSession()), template, args, person, url, menuIdx);
        }
    }

    public static void info(
            HttpSession session,
            String template,
            Object... args) {
        if (debugFlag) {
            Person person = (Person) session.getAttribute(CC.CLIENT);
            info(getSB(session), template, args, person);
        }
    }

    /**
     * The first element is template, must not be null, it has to contains right number of "{}" to match the following
     * parameters. Some parameters might be ignored base on how many {} contains in template.
     */
    public static void info(
            StringBuilder sb,
            Object... args) {
        if (debugFlag) {
            if (args.length == 0) {
                args = new String[] { "" };
            }
            sb.append("-INFO:");
            String template = args[0].toString();
            if (args.length > 1 && args[1] != null && args[1] instanceof String[] && ((String[]) args[1]).length == 3) {
                String[] menuIds = (String[]) args[1];
                args[1] = menuIds[0] + "_" + menuIds[1] + "_" + menuIds[2];
            } else if (args.length > 1 && args[1] != null && args[1] instanceof Object[]) {
                Object[] subParamAry = (Object[]) args[1];
                int length = subParamAry.length;
                Object[] enlargedArgs = new Object[args.length + (length - 1)];
                enlargedArgs[0] = args[0];
                System.arraycopy(args, 2, enlargedArgs, 1 + length, args.length - 2);
                for (int i = 0; i < length; i++) {
                    enlargedArgs[i + 1] = subParamAry[i];
                }
                args = enlargedArgs;
            }

            // don't use break, because we need to make sure all the args not null
            for (int i = 1; i < args.length; i++) {
                if (args[i] == null) {
                    args[i] = "";
                }
                int p = template.indexOf('{');
                if (p >= 0) {
                    sb.append(template.substring(0, p));
                    sb.append(args[i].toString());
                    template = template.substring(p + 2);
                }
            }
            if (!StringUtils.isBlank(template)) {
                sb.append(template);
            }
            sb.append("<br>");

            // above code has replaced null element to "".
            switch (args.length) {
                case 1:
                    logger.info(args[0].toString());
                    break;
                case 2:
                    logger.info(args[0].toString(), args[1].toString());
                    break;
                case 3:
                    logger.info(args[0].toString(), args[1].toString(), args[2].toString());
                    break;
                case 4:
                    logger.info(args[0].toString(), args[1].toString(), args[2].toString(), args[3].toString());
                    break;
                case 5:
                    logger.info(args[0].toString(), args[1].toString(), args[2].toString(), args[3].toString(),
                            args[4].toString());
                    break;
                case 6:
                    logger.info(args[0].toString(), args[1].toString(), args[2].toString(), args[3].toString(),
                            args[4].toString(), args[5].toString());
                    break;
                case 7:
                    logger.info(args[0].toString(), args[1].toString(), args[2].toString(), args[3].toString(),
                            args[4].toString(), args[5].toString(), args[6].toString());
                    break;
                case 8:
                    logger.info(args[0].toString(), args[1].toString(), args[2].toString(), args[3].toString(),
                            args[4].toString(), args[5].toString(), args[6].toString(), args[7].toString());
                    break;
            }
        }
    }
}

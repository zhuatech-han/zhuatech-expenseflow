// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.expenseflow;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** 金额、单据状态、凭证摘要与独立审批规则。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class ExpensePolicy {
  private ExpensePolicy() {}

  /** 人民币金额精确转为分，禁止负数、过大金额和超过两位小数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static long cents(BigDecimal v, boolean zero) {
    try {
      long n = v.movePointRight(2).longValueExact();
      if (n < (zero ? 0 : 1) || n > 10_000_000_000L) throw new ArithmeticException();
      return n;
    } catch (NullPointerException | ArithmeticException e) {
      throw new Problem(400, "INVALID_AMOUNT");
    }
  }

  /** 严格验证客户端版本号。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void version(ExpenseCase c, Long v) {
    if (v == null || !v.equals(c.version)) throw new Problem(409, "STALE_VERSION");
  }

  /** 拒绝非法状态流转。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void state(ExpenseCase c, String... allowed) {
    if (!Set.of(allowed).contains(c.status)) throw new Problem(409, "INVALID_STATE");
  }

  /** 月份是预算归属，不接受含日的任意日期。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String month(String s) {
    try {
      if (s == null || !s.matches("20[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
      return YearMonth.parse(s).toString();
    } catch (Exception e) {
      throw new Problem(400, "INVALID_MONTH");
    }
  }

  /** 金额相加检查上界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static long sum(long a, long b) {
    long n = Math.addExact(a, b);
    if (n > 10_000_000_000L) throw new Problem(400, "INVALID_AMOUNT");
    return n;
  }

  /** 对受控凭证或命令内容计算摘要，不作为电子签名。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** 固定 UTF8 编码计算文本摘要。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String hash(String s) {
    return hash(s.getBytes(StandardCharsets.UTF_8));
  }

  /** 主管、财务和申请人必须互相独立。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void independent(Long applicant, Long manager, Long finance) {
    if (manager == null
        || finance == null
        || manager.equals(applicant)
        || finance.equals(applicant)
        || manager.equals(finance)) throw new Problem(400, "INDEPENDENT_REVIEW_REQUIRED");
  }
}

// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.expenseflow;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 独立检验精确金额、日期、审批与摘要规则。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class ExpensePolicyTest {
  @Test
  void centsNeverUsesBinaryFloatingPoint() {
    assertEquals(1001, ExpensePolicy.cents(new BigDecimal("10.01"), false));
    assertEquals(10, ExpensePolicy.cents(new BigDecimal("0.10"), false));
  }

  @Test
  void subcentRejected() {
    assertThrows(Problem.class, () -> ExpensePolicy.cents(new BigDecimal("1.001"), false));
  }

  @Test
  void negativeAndZeroCostRejected() {
    assertThrows(Problem.class, () -> ExpensePolicy.cents(new BigDecimal("-1"), false));
    assertThrows(Problem.class, () -> ExpensePolicy.cents(BigDecimal.ZERO, false));
    assertEquals(0, ExpensePolicy.cents(BigDecimal.ZERO, true));
  }

  @Test
  void oversizedRejected() {
    assertThrows(Problem.class, () -> ExpensePolicy.cents(new BigDecimal("100000000.01"), false));
  }

  @Test
  void monthStrict() {
    assertEquals("2026-10", ExpensePolicy.month("2026-10"));
    assertThrows(Problem.class, () -> ExpensePolicy.month("2026-13"));
    assertThrows(Problem.class, () -> ExpensePolicy.month("2026-10-01"));
  }

  @Test
  void independentStages() {
    ExpensePolicy.independent(1L, 2L, 3L);
    assertThrows(Problem.class, () -> ExpensePolicy.independent(1L, 1L, 3L));
    assertThrows(Problem.class, () -> ExpensePolicy.independent(1L, 2L, 2L));
    assertThrows(Problem.class, () -> ExpensePolicy.independent(1L, 2L, 1L));
  }

  @Test
  void versionAndState() {
    var c = new ExpenseCase();
    c.version = 3L;
    c.status = "FINANCE";
    ExpensePolicy.version(c, 3L);
    assertThrows(Problem.class, () -> ExpensePolicy.version(c, 2L));
    assertThrows(Problem.class, () -> ExpensePolicy.state(c, "DRAFT"));
  }

  @Test
  void hashesStableAndSensitiveToContent() {
    assertEquals(64, ExpensePolicy.hash("验收").length());
    assertEquals(ExpensePolicy.hash("验收"), ExpensePolicy.hash("验收"));
    assertNotEquals(ExpensePolicy.hash("A"), ExpensePolicy.hash("a"));
  }
}

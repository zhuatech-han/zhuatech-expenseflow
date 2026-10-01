// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.expenseflow;

import jakarta.persistence.*;
import java.time.*;

/** 受单据权限保护的原始凭证；数据库持久化避免公开路径与文件穿越。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "expense_evidence")
public class Evidence {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "case_id")
  public Long caseId;

  @Column(name = "content_type")
  public String contentType;

  @Column(name = "extension")
  public String extension;

  @Column(name = "hash")
  public String hash;

  @Column(name = "size")
  public long size;

  @Column(name = "frozen")
  public boolean frozen;

  @Lob
  @Column(name = "payload", columnDefinition = "longblob")
  public byte[] bytes;

  @Column(name = "created_at")
  public Instant createdAt;
}

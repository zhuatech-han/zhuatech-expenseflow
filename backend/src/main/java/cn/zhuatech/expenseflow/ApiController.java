// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.expenseflow;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 费用、预算、凭证与管理接口；所有业务校验在事务服务中完成。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final ExpenseService service;
  final AdminService admin;

  public ApiController(ExpenseService service, AdminService admin) {
    this.service = service;
    this.admin = admin;
  }

  /** 授权人员、部门和字典目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return service.options();
  }

  /** 查询费用并按数据范围分页。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/cases")
  public Object list(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "") String kind,
      @RequestParam(defaultValue = "") String category,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return service.list(search, status, kind, category, page, size, sort);
  }

  /** 创建本人费用申请或报销草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/cases")
  public Object create(@RequestBody ExpenseService.Draft v) {
    return service.create(v);
  }

  /** 读取单据、流转、预算和凭证元数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/cases/{id}")
  public Object detail(@PathVariable Long id) {
    return service.detail(id);
  }

  /** 修改本人草稿；服务端版本保护。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/cases/{id}")
  public Object save(@PathVariable Long id, @RequestBody ExpenseService.Draft v) {
    return service.save(id, v);
  }

  /** 删除未送审草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/cases/{id}")
  public Object delete(@PathVariable Long id, @RequestParam Long version) {
    service.delete(id, version);
    return Map.of("ok", true);
  }

  /** 有限费用状态命令与付款登记、冲销。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/cases/{id}/commands/{action}")
  public Object act(
      @PathVariable Long id, @PathVariable String action, @RequestBody ExpenseService.Command v) {
    return service.act(id, action, v);
  }

  /** 上传经过文件头和大小校验的凭证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping(value = "/cases/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Object upload(
      @PathVariable Long id, @RequestParam Long version, @RequestPart("file") MultipartFile file) {
    return service.upload(id, version, file);
  }

  /** 凭证只通过授权下载访问，禁用内联解释与缓存。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/cases/{id}/evidence/{eid}")
  public ResponseEntity<byte[]> download(@PathVariable Long id, @PathVariable Long eid) {
    var e = service.download(id, eid);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(e.contentType))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=evidence-" + eid + "." + e.extension)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header("X-Content-Type-Options", "nosniff")
        .body(e.bytes);
  }

  /** 删除未用于送审的附件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/cases/{id}/evidence/{eid}")
  public Object removeEvidence(
      @PathVariable Long id, @PathVariable Long eid, @RequestParam Long version) {
    return service.removeEvidence(id, eid, version);
  }

  /** 月预算目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/budgets")
  public Object budgets() {
    return service.budgets();
  }

  /** 设置部门类别月预算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/budgets")
  public Object createBudget(@RequestBody ExpenseService.BudgetInput v) {
    return service.saveBudget(null, v);
  }

  /** 带版本调整额度。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/budgets/{id}")
  public Object saveBudget(@PathVariable Long id, @RequestBody ExpenseService.BudgetInput v) {
    return service.saveBudget(id, v);
  }

  /** 本人、审批和付款待办。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/workbench")
  public Object workbench() {
    return service.workbench();
  }

  /** 费用与预算统计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  public Object dashboard() {
    return service.dashboard();
  }

  /** 授权审计列表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  public Object audit() {
    return service.audit();
  }

  /** 导出授权单据，保持业务数据纯净。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/cases/{id}/report.json")
  public ResponseEntity<String> export(@PathVariable Long id) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=expense-" + id + ".json")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(service.export(id));
  }

  /** 查询系统资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object listAdmin(@PathVariable String type) {
    return admin.list(type);
  }

  /** 创建系统资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object createAdmin(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 修改系统资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object updateAdmin(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 删除无引用的系统资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object deleteAdmin(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}

# 通知连接恢复修复

## 已确认的问题

旧实现仅在进程启动时读一次来源设置并调用 `requestRebind`。进程启动时没有选择来源、随后首次启用通知识别，以及从系统授权页面返回已有进程，都缺少恢复入口。断连回调直接请求一次，失败没有后续反馈。应用选择页只提供授权入口，不能区分授权与实际连接。

此前设备检查发现：系统保留通知访问授权，但本服务不在 Live notification listeners 中，Activity 服务列表也没有本服务；重新授予权限后仍未连接。这说明该设备确实没有绑定服务，不能仅修正文案或将重绑请求返回视为成功。具体系统阻断原因尚未确定。

## 本次修复

- 启动、来源集合变化、主页面恢复前台、通知服务断连统一进入连接恢复流程。
- 同一时刻只运行一轮恢复；每轮最多请求三次，每次等待系统回调最多五秒。取消选择或撤销权限后停止请求，不做无限后台轮询。设置读取及 Binder 请求在 IO 线程执行。
- 第二轮请求在 Android 14 及以上先 requestUnbind 再 requestRebind，处理普通重绑无效的残留状态；解绑和重绑成对执行，取消任务不会停在解绑阶段。低版本继续普通重绑。
- Android 8.1 及以上通过 NotificationManager 查询本组件的授权；Android 8.0 保留 Secure 设置查询。
- 已授权、正在连接、连接失败和实际连接分别反馈。仅 `onListenerConnected` 表示连接成功。
- 通知来源页面和设置页使用相同状态说明，来源页面提供重试及应用后台设置入口。后台设置提示是排查建议，不表示已经证明后台限制是根因。
- 保留服务组件名、通知来源选择和用户记录，不改变识别过滤规则或数据库。

API 依据：[NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService) 与 [NotificationManager](https://developer.android.com/reference/android/app/NotificationManager#isNotificationListenerAccessGranted(android.content.ComponentName))。AOSP 的连接/断连回调默认实现为空，未调用 super 不是此前未绑定的证据。

## 验证

新增九项单测验证：请求返回不能当作成功、重试有上限、系统连接后停止、已连接时不重绑、撤权/清空来源停止、取消任务不吞异常，状态文案区分授权/连接/关闭，以及第二轮才清理旧绑定、正常连接时不清理绑定。

最终代码的 321 项单测无失败或错误，识别语料 47/47 通过，Lint 无错误。正式签名 arm64 Release 的 R8、资源收缩及打包通过。

设备已完成正式签名 arm64 包覆盖安装。用户调整 Thanox 权限并重启后，系统 Live notification listeners 和实际服务绑定记录均出现本服务，页面显示“通知识别已连接”；后续覆盖安装仍保持连接。此前未绑定的具体系统原因未隔离，不能将恢复全部归因于应用重绑代码。

临时选择 Shell 后，本地合成取件通知成功入库；QQ 登录验证码通知未入库；取消 Shell 后发出的新取件通知未入库。测试来源已取消，恢复原短信与微信选择，只清理本轮合成记录。真实短信、微信与购物平台推送未逐一重测，本地合成通知通过不代表所有外部平台推送均已验证。

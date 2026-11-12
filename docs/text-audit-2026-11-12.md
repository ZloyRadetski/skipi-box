# Text audit, 2026-11-12

Per-batch findings: texts compared with the code behind them, in all five languages. Fixes are applied in the shared string files; items under 'uncertain' were left unchanged and need a product decision.

---

## Audit a1 (settings 1), 250 keys

## 1. Behavior mismatches (text said something other than the code does)

- settings_color_mode (SkipiAppearanceSections.kt:85): en said "Theme" (same as the section title settings_theme, and ru called it "Цветовой режим"). The dropdown is system/light/dark/AMOLED (summary settings_color_mode_summary). New en "Color mode"; fa "حالت رنگ"; zh "颜色模式". fa "موضوع" (= "topic") for settings_theme corrected to "تم".
- settings_category_subscriptions_summary (SkipiSubscriptionSettingsScreen.kt:145-230): said "Fetch timeout, User-Agent, ping check"; the screen also has device identifiers and expiry notifications/reminders. New en: "Fetch timeout, User-Agent, device identifiers, expiry notifications, ping check" (+ ru/fa/zh).
- settings_connection_display_mode_summary (SkipiAppearanceSections.kt:513): fa and zh said "choose between compact and classic"; there are three modes (Compact, Classic, Legacy). fa/zh aligned with en; ru "плашки" changed to "виджета" to match the title. settings_connection_display_mode_legacy was missing in fa/zh (added).
- settings_kill_switch_summary: fa and zh described a different behavior ("block traffic when VPN drops"); en/ru say it opens Android settings (SettingsVpnPage.kt:91-94). fa/zh aligned with en.
- settings_wake_lock_summary: fa/zh mention only sockets; code takes PARTIAL_WAKE_LOCK (SkipiVpnService.kt:595-603, CPU). fa/zh aligned with en.
- settings_seamless_network_switching_summary (SkipiVpnService.kt:409, 627): fa/zh said "instantly rebind tunnel"; en says Android picks the network. fa/zh aligned with en.
- settings_bottom_bar_size_summary: fa/zh said "floating bottom bar"; en/ru say bottom bar. Aligned.
- settings_category_logs fa said "Logs and diagnostics" (en "Logs"); fixed. settings_category_appearance_summary fa said "theme, language, connection style" (en: theme, colors, fonts, layout); fixed.
- Broken translations: settings_tun_mtu fa "PERSON" and zh "人" (MTU); settings_general fa "ژنرال" (army general) and zh "一般的"; settings_resource_files_source_custom zh "风俗" (= customs); settings_mux fa "ماکس"; zh "袖珍的" for Compact; size/missing/ready zh/fa literal translations; missing spaces before placeholders in fa/zh ({mtu}, {name}, {time}, {size}, {tcp}); fixed.
- Missing in fa/zh (filled): settings_classic_button_size (+_summary, _small, _medium, _large, _giant), settings_strict_full_tunnel (+_summary), settings_connection_display_mode_legacy. Missing only in fa (filled): settings_header_general/tools/logs/layout, settings_notifications_title, settings_localization_and_feedback, settings_category_general (+_summary). Missing in zh (filled): settings_header_integration, settings_header_diagnostics.

Ranges verified against code and correct: MTU 1280-65535, keep-alive 1-300 s, TCP user timeout 1000-120000 ms (VpnDefaults.kt), port 1-65535, mux TCP -1/0/1-128 (SettingsDrafts.kt:155). Strict full tunnel text matches StrictFullTunnel.kt.

## 2. Duplicates
- settings_theme and settings_color_mode both "Theme" in old en (now differ).
- settings_header_general "General" = settings_general "General" (settings_general is unused).
- settings_header_appearance "Appearance" = settings_category_appearance (ru differs: "Оформление" / "Внешний вид").
- settings_header_logs "Logs" = settings_category_logs "Logs"; settings_header_subscriptions = settings_category_subscriptions ("Subscriptions & Servers"; ru was "серверы" vs "узлы", fixed to "серверы").
- settings_external_interfaces_none / settings_mux_none "Not enabled"; settings_external_interfaces_selected / settings_inbound_selected "Enabled: {..}" (same meaning).
- Size labels "Small/Medium/Large" repeated in bottom_bar_size, classic_button_size, font_weight.
- settings_theme_color / settings_color_accent: "Accent color".

## 3. Uncertain, product owner decision (texts left unchanged)
- Unused keys (no reference in app/, shared/, desktopApp/): settings_advanced, app_icon_summary, bpf2socks_bridge_port, bpf2socks_socks5_port, category_integration_summary, core, external_interfaces* (all), font_family_summary, font_size_* (except title), font_weight_summary, general, header_diagnostics, header_integration, ignored_interfaces* (all), inbound* (all), ipv6_summary, ipv6_prefer(+summary), local_proxy_summary_fixed/all_interfaces, private_addresses* (all), sniffing_summary, sniffing_route_only(+summary), theme_color, theme_color_custom, theme_color_custom_seed(+summary). Not audited for logic; only blatant translation faults fixed. Consider deleting them.
- settings_kill_switch_summary: the switch also makes the tunnel keep blackholing traffic after a failed network-handover recovery (SkipiVpnService.kt:835, VpnLeakProtection.kt:29) and opens Android VPN settings on enabling (SettingsVpnPage.kt:91); the text only mentions opening settings. Owner to decide wording.
- settings_seamless_network_switching_summary: on a real handover the core runtime is restarted in place (SkipiVpnService.kt:620-634) while the TUN stays; "without restarting the tunnel" is acceptable but imprecise.
- settings_close_to_background (DesktopWindowLifecycle.kt:15): works only when a system tray is available; otherwise the window close exits. Text does not say so.
- settings_deletion_confirmation_summary lists "servers, routes, groups, or resources"; the shared dialog (LocalConfirmDeletion) is also used elsewhere (subscriptions etc. likely). Scope not fully verified.
- settings_color_mode ru "Цветовой режим" kept; settings_theme ru "Тема оформления" kept.
- settings_seamless_network_switching ru "Бесшовный роуминг сетей" / zh "无缝网络漫游" say "roaming" while en says "switching"; left as is.

---

## Audit a2 (settings 2): report

250 keys checked, 76 patched (patch file: a2-patch.json).

## 1. Behavior mismatches

1. settings_restore_confirm_summary (SettingsBackupResetPage.kt:122-150, AppBackupMapper.kt:359)
   - Old: said a "ROOT boot script will be disabled" and "run mode will switch to VPN Service".
   - Code: ROOT mode no longer exists. `RunModeVpnService` is the only mode, and no ROOT boot-script code is left in app/ or shared/. Restore calls `shutdown()`, then replaces the state. The backup also carries configurations (`trafficConfigs`), which the text did not list.
   - New en: "Current settings, proxy servers, subscriptions, configurations, routing rules and per-app proxy lists will be replaced. The running proxy will be stopped." ru/fa/zh match.
2. settings_mux_udp443_reject (XrayCoreFeatureDefaults.kt:11, XrayOutboundSupport.kt:173)
   - Old: "No proxy". ru said "Блокировать (Без прокси)".
   - Code: the option writes `xudpProxyUDP443 = "reject"`, which is the Xray default and rejects UDP/443 (QUIC).
   - New: "Block".
3. settings_mux_udp443_skip (same code, value "skip")
   - Old: "Native". ru said "Нативно (Пропускать напрямую)". zh was "本国的" ("national").
   - Code: "skip" means QUIC goes through the proxy outbound without Mux. It does not go direct.
   - New: "Native (no Mux)" in en/ru/fa/zh.
4. settings_mux_display_disabled: zh was "残疾人" ("disabled people"), now "已禁用". fa now "غیرفعال".
5. zh-rCN held Traditional Chinese in 4 keys, now identical to zh: settings_tunnel_ip_show_on_home, settings_tunnel_ip_show_on_home_summary, settings_tunnel_ip_mask_on_home, settings_tunnel_ip_mask_on_home_summary.
6. settings_restore_backup_created_at, zh: "创建者" ("creator") is now "创建时间" ("creation time").
7. settings_color_protocol_trojan, zh: "木马协议" ("trojan horse protocol") is now "Trojan 协议". fa now "پروتکل Trojan" (protocol names stay Latin, as for the other protocols).
8. settings_dns_hosts_format, fa and zh: the example had been translated with localized separators ("域：地址1，地址2"). The user types it literally, so it is now `domain:address1,address2,…`.
9. settings_enable_material_you_summary: "colour" is now "color", the spelling used elsewhere.
10. Persian and Chinese strings that were wrong or unreadable:
    - fa: settings_service_control_doze_note (the English "stop wins" was translated literally), settings_service_control_enable_summary ("مستقر باشید"), settings_access_logs ("دسترسی به سیاهههای مربوط"), settings_sniffing ("بو کشیدن", literally "smelling"), settings_enable_material_you.
    - zh: settings_service_control_doze_note ("停止获胜"), settings_logcat ("日志猫", literally "log cat").
11. Missing spaces before placeholders in fa, so the text ran into the number or word. Fixed in settings_fragment_selected, settings_restore_backup_version, settings_restore_backup_created_at, settings_restore_backup_counts, both settings_restore_warning_missing_* keys, settings_tunnel_memory_kilobytes, settings_user_agents_summary and settings_local_proxy_copied_toast.
12. Terminology made consistent:
    - fa DNS: "DNS پروکسی" now matches "DNS مستقیم".
    - fa service: "سرویس", not "خدمات".
    - fa auth: "احراز هویت", not "مجوز".
    - zh fragment: 分片 across the whole fragment sheet, not a mix of 分段 and 片段.
    - zh DNS domain: 域名, not 域.
    - zh FakeDNS: not "假DNS".
    - zh schedule: 计划控制, not 预定控制.
    - zh weekday: 星期, not 工作日 (which means "working day").
    - ru backup: "резервная копия", not "бэкап", in two strings.
    - ru settings_logs: "Логи".
    - ru settings_category_ip_info: "IP-адресе".
    - ru settings_local_proxy_auth_mode_off: "Выключено" (was the imperative "Отключить").
    - ru "Разрешать прокси через прямой DNS" read as "allow", so it is now "Определять адреса прокси через прямой DNS".
    - ru direct_dns_resolve..._summary: dropped the "строго" ("strictly") that the English never promised.

Missing translations filled in fa, zh and zh-rCN (33 entries, 11 keys): settings_dns_server_system_hint, settings_tun_mode_title, settings_tun_mode_summary, settings_tun_mode_unavailable, settings_launch_on_login_title, settings_launch_on_login_summary, settings_navigation_style, settings_navigation_style_summary, settings_navigation_style_bottom, settings_navigation_style_sidebar, settings_background_photo_failed.

## 2. Duplicates and unused keys

Duplicates (same English, same meaning):
- settings_http_proxy_port and settings_local_proxy_http_port ("HTTP proxy port"). Only the second is used.
- settings_local_proxy_auth_mode_off ("Off") likely duplicates other "Off" strings in other batches. Not checked here.

Keys with no usage in app/, shared/ or desktopApp (dead, can probably be deleted):
- settings_http_proxy
- settings_http_proxy_summary
- settings_http_proxy_port
- settings_transparent_proxy_port
- settings_tun2socks_socks5_port
- settings_sniffing
- settings_ipv6
- settings_about

## 3. Uncertain, needs the product owner (texts left unchanged)

- settings_classic_show_floating_power_button: the title says "Compact power button" but the summary says "floating power button in classic mode". ProxyHomeScreen.kt:288-289 shows the floating button when the connection mode is Compact OR this flag is on. The title and summary describe different things.
- settings_reset_vpn_confirm_summary and settings_reset_vpn_summary (AppStateReset.kt:19-82): the reset also does the following, none of which the text mentions. The text says only "tunnel settings and global VPN defaults".
  - It clears `shadowrocketPolicyGroups`.
  - It resets the per-app proxy mode and list.
  - It resets the run mode, kill switch and DNS settings.
  - It keeps the SOCKS and HTTP login. Should the text say that credentials are kept?
- settings_dns_server_system_hint: "Use localhost to use Android system DNS, including Private DNS." This sheet is shared with Desktop. I did not verify what `localhost` does on Desktop. If it differs, the text needs a platform split.
- settings_subscription_device_headers_summary: "Android version, and device model" is Android wording. Not verified on Desktop.
- settings_mux_xudp_concurrency_summary: 0 is shown as "same as TCP", while the stored default is "16" (DefaultMuxXudpConcurrency). The text is consistent with the display, but the default of 16 is not mentioned anywhere.
- fa: log_retention keys use Persian digits, the rest of the fa file uses Latin digits. Left unchanged.
- fa uses both "پراکسی" and "پروکسی" across the app. I used "پروکسی" in new text and did not touch the rest.
- settings_background_style_photo, zh: uses "壁纸" (wallpaper) while settings_background_photo_failed (new) says "照片/图片". Acceptable, left as is.

---

## Batch a3 report (desktop misc, tray, widget, import/core/system-proxy messages)

## 1. Behavior mismatches
- desktop_settings_reset_system_proxy_summary: old "Restore the previous system proxy settings." Code: DesktopSettingsScreen.kt:816 -> Main.kt:1971 -> systemProxyManager.forceClear() (DesktopWindowsSystemProxy.kt:404, Linux :198, Mac :170) sets ProxyEnable=0 / mode none / services off and drops the lease. It turns the proxy OFF, it does not restore previous values. New: "Turn off the system proxy and clear the proxy state saved by SKIPI." (ru/fa/zh updated).
- desktop_settings_tunnel_running_summary: old "Changes apply after reconnecting." Code: Main.kt:1960 (`desktopSettingsNeedReconnect` -> `tunnel.requestReconnect`, list in DesktopLocalProxyRuntime.kt:97) reconnects automatically when a connection setting changes while running. New: "...Changing connection settings reconnects it automatically."
- desktop_settings_use_system_proxy_summary: old "configures the system HTTP/SOCKS proxy". Windows (DesktopWindowsSystemProxy.kt:51) sets only HTTP/HTTPS (`http=..;https=..`), SOCKS only on Linux/macOS. New text is platform-neutral and mentions restoring the previous settings.
- quick_settings_tile_label: fa "کشتی" and zh "船" ("ship") translate the brand name; en/ru keep SKIPI. Set to SKIPI (AndroidManifest.xml:305 label).
- quick_settings_tile_processing zh "加工…" means manufacturing/machining; now "处理中…". tile_running zh "代理运行" made a full phrase "代理正在运行".
- widget_speed_format: en used ↑ / · escapes while the other languages hold literal characters; replaced with literal characters (same rendering, no escape in shared file).
- notification_vpn_connected/disconnected/connect_failed/disconnect_failed: missing in fa, zh, zh-rCN (fell back to English). Added (used in desktopApp Main.kt:1248 and Android).
- ru grammar/typos: desktop_core_reconnected_with_proxy (word order), desktop_core_tunnel_proxy_started (plural agreement), "возвращен"/"сохраненной" (ё as elsewhere), "HTTPS ссылка"/"Mailto ссылка" (hyphen / word order), "Не удалось разобрать серверов: N" (ungrammatical; now "Не разобрано серверов: N."), data_dir strings "Каталог" -> "Папка" to match en "folder" and the neighbouring "папку" strings.

## 2. Duplicates
- desktop_settings_folder_open_failed and desktop_settings_data_dir_open_failed: identical en "Failed to open the folder: %1$s" (same meaning).
- desktop_settings_storage_header ("STORAGE") vs desktop_settings_storage ("Storage"): same word, header vs row label (not true duplicates).
- desktop_import_section_servers/subscriptions/configs vs desktop_purpose_servers and the "servers/subscriptions/configs" parts of desktop_import_count_*: same nouns in lowercase, different grammatical roles in ru (nominative vs genitive).

## 3. Uncertain, needs owner decision (texts unchanged)
- desktop_status_error_markers (DesktopText.kt:19): a status line is an error only if it contains a marker, plus hardcoded "failed" and "rejected". Messages such as "Config not saved: ...", "Config not imported: ...", "Import not saved: ...", "Config not updated: ..." (en) contain no marker, so they are not styled as errors in en; ru/fa/zh have the same gap ("не сохранён", "не импортирован", "ذخیره نشد", "未保存"). Either add markers like "not saved|not imported|not updated" per language or rephrase those texts to "Failed to ...". Product decision.
- Terminology: widget_description says "VPN connection", widget_toggle_description/quick tile say "proxy"; app mixes VPN/proxy/tunnel. Left as is.
- "lease" (desktop_system_proxy_*): ru "аренда", fa "اجاره", zh "租约" are literal calques of an internal concept; user-facing wording may deserve a plainer term (e.g. "saved proxy state"). Left unchanged.
- Many "X is not connected in Desktop" texts (desktop_home_*_not_connected, tcp_cancel_unavailable, qr_*): developer-style fallbacks when a handler is null; users should rarely see them. Check whether these features are still unwired; if so the texts are fine, otherwise they are dead strings.
- ru "runtime" (desktop_settings_recheck_runtime) left in Latin, same for fa; zh uses 运行时.

---

## Batch a4 (proxy keys, 255 keys)

## 1. Behavior mismatches
- proxy_editor_strategy_group_select_servers_summary: old en said "...or auto balancers". The member selector (app/src/main/kotlin/features/proxy/server/editor/StrategyGroupMemberSelectorPage.kt:114) labels that source with `proxy_server_list_auto_balancers`, whose text is already "Proxy groups". New en: "...manual servers, or proxy groups." ru/fa/zh updated to match.
- proxy_server_list_latency_failed: ru said "Тайм-аут", but the string is shown for any failed latency check (ProxyHomePanels.kt:118, ProxyHomeServerDetails.kt:62, ProxyHomeServerListItem.kt:98), not only timeouts. New ru: "Ошибка".
- proxy_editor_strategy_group_show_on_home (fa only): said "Auto-balancers", while en/ru say "Proxy groups" (ProxyServerComponents.kt:243). Fixed in fa. Same fix in fa for show_on_home_summary and proxy_editor_strategy_group_type ("strategy group" to "proxy group", as in en).
- Wireguard spelling: en/ru "Add Wireguard" vs "WireGuard" everywhere else (validation texts). New: "Add WireGuard".
- Capitalization of en list actions: "Add Strategy group", "Add Chain proxy", "Add Custom", "Health Check" changed to sentence case like the neighbouring strings.
- zh translations that were wrong in meaning (machine output): Trojan "木马", Socks "袜子", Hysteria2 "歇斯底里2", Wireguard "线卫", hops "啤酒花" (beer hops), Member "会员" (membership), Remarks "评论" (comments), Flow "流动", Refreshing "清爽…", Sort "种类", Name "姓名" (person name), Transport "运输" (freight), Reality "现实设定", "更多行动". All replaced.
- fa translations wrong in meaning: Pause "مکث کنید", Resume "رزومه" (CV), Transport "حمل و نقل" (freight), port hopping "پرش بندر" (harbor), salamander "سمندر" (animal), Trojan "تروجان", Socks "جوراب" (sock), Reality "واقعیت", Remarks "اظهارات", Burst probe "انفجار کاوشگر". Replaced.
- Port hopping interval minimum of 5 seconds (ProxyServerValidation.kt:376) matches the text; MTU default 1420 (Wireguard.kt:28/AmneziaWg.kt:40) matches.
- Placeholder spacing: fa strings glued {count}/{name}/%1$d to words, and zh lacked measure words (个); fixed for all placeholder strings. Placeholders are unchanged.
- Missing translations filled: proxy_editor_awg_s3, proxy_editor_awg_s4 (fa, zh, zh-rCN).

## 2. Duplicates
- proxy_app_list_more_actions and proxy_server_list_more: both "More actions".
- proxy_editor_strategy_group_show_on_home and proxy_group_display_mode_title/proxy_server_list_auto_balancers: related wording ("Proxy groups") but different meaning; not duplicates.
- proxy_editor_password and proxy_editor_password_optional/auth: different meaning, no merge.
- Key count pairs with equal en in other batches not checked.

## 3. Uncertain / owner decision
- proxy_server_copy_url ("Share URL"), proxy_server_copy_qr_code ("QR code"), proxy_server_copy_full_json ("Full JSON"): keys and action ids are `copy:*` and the action is CopyServer (copies to clipboard, ProxyHomeServerListItem.kt:50-69), but the label says "Share". Depends on the menu header (not shown in code); suggest "Copy URL" or leave if grouped under a "Copy" header. Unchanged.
- proxy_editor_strategy_group_fallback: en "Fallback (Priority)" but ru/zh/fa show only "(Fallback)" without Priority. Unchanged.
- proxy_server_list_delete_invalid / *_invalid_deleted: ru says "нерабочие конфигурации" (non-working) while en says "invalid" (validation failure). Check which one the code does; unchanged.
- proxy_editor_discard (ru "Отменить") next to title "Отменить изменения?" and button "Вернуться": maybe confusing, "Отбросить" is clearer; unchanged.
- proxy_editor_strategy_group_selected_servers_summary / servers_count: "1 servers" has no plural handling in any language.
- proxy_editor_strategy_group_show_on_home_summary mentions "Proxies screen" (en) / "главном экране" (ru); screen name not verified.
- "REALITY" vs "Reality" capitalisation inconsistent (settings title vs validation); left.
- Unused keys not found in code search: proxy_server_list_ping_check (no references seen); translations fixed anyway.

---

## Audit a5 (configs / routing), 278 keys, 134 patched

## 1. Behavior mismatches (text said something different from the code)

- `configs_dns_direct_fallback_proxy` / `_summary` (fa, zh). Logic: `SkipiTrafficConfigDnsTunnelEditors.kt:169`, flag `enableDirectDnsForProxyServerDomains` (default true, `AppState.kt:223`). EN/RU: "Direct DNS for proxy servers" = proxy server hostnames are resolved via Direct DNS. fa/zh still said "if Direct DNS fails, fall back to proxy DNS" (the old meaning, the opposite direction). New fa/zh follow the English text.
- `configs_dns_tun_dns` / `_summary` (fa, zh). Logic: `...DnsTunnelEditors.kt:170,297-300`, field `tunVpnDns`, the virtual IPv4 DNS address of the TUN. fa/zh said "hijack/redirect all DNS traffic through TUN" (a different feature). New fa/zh follow the English text.
- `configs_dns_summary` (fa, zh; en naming). Used for the DNS section (`DesktopConfigsScreen.kt:545`, `TrafficConfigEditorPages.kt:121`). EN/RU list VPN local DNS, Proxy/Direct DNS (DoH, local DoQ, DoU, TCP), FakeDNS; fa/zh said "direct and proxy DNS servers plus domain rules". Now the same as EN. EN "VPN Local DNS" became "Local VPN DNS" to match the `configs_local_dns` title.
- `configs_dns_master_switches` (fa "main switches", zh 主开关). The EN/RU heading is "DNS Behavior". Fixed fa/zh.
- `configs_dns_empty_servers` (fa/zh said "tap Add", EN says only "No DNS servers configured"). `configs_dns_server_invalid`, `configs_dns_domain_invalid` and `configs_dns_hosts_invalid` (fa/zh dropped the examples that EN/RU show). Aligned with EN.
- `configs_fake_dns_summary` (ru). Logic: the switch is disabled unless Local VPN DNS is on (`...DnsTunnelEditors.kt:167`). The EN text "Requires Local VPN DNS" is correct. RU now names the switch as it is titled: «Локальный DNS VPN».
- `routing_edit_rule` (ru). EN is "Edit routing rule", RU was a bare "Редактировать". Fixed.
- `routing_info_desc_wildcard` (fa). The "?" was the Persian question mark, so it did not show the real wildcard character. Fixed.
- `routing_info_desc_protocol` (zh). The list lacked bittorrent. Fixed.
- `configs_rules_final_missing` (zh translated FINAL,PROXY into words, fa used a Persian comma). The tokens are literal, so now `FINAL,PROXY` everywhere.
- Wrong or garbled fa/zh translations fixed:
  - zh `configs_name` 姓名 (a person's name) -> 名称.
  - zh `routing_ip_label` 知识产权规则 (intellectual property) -> IP 规则.
  - fa `configs_tun_mtu` "PERSON" and zh 人 -> MTU.
  - zh 港口 (harbour) -> 端口, and fa بندر -> پورت.
  - zh `routing_outbound_*` 代理人/直接的/堵塞 -> 代理/直连/拦截.
  - zh `configs_dns_cloudflare` 云耀 -> Cloudflare.
  - zh `configs_dns_custom` 风俗 -> 自定义.
  - zh `configs_hevtun` 赫夫屯 -> HevTun.
  - zh `configs_rules_type` 比赛类型 -> 匹配类型.
  - fa `configs_general_title` ژنرال (general, the military rank) -> عمومی.
  - zh `configs_general_title` 一般的 -> 常规.
  - zh `configs_rules_value` 价值 -> 值.
  - fa `configs_rules_value` ارزش -> مقدار.
  - zh `routing_domain_strategy` 领域策略 -> 域名解析策略.
  - fa `configs_sniffing` بو کشیدن (literally "to sniff") -> Sniffing.
  - zh `configs_fragment` 碎片化 -> TLS 分片.
  - `configs_proxy_groups_summary` (fa/zh were garbled, "backback" typo).
  - fa `configs_update_locked_warning_summary` translated the technical key `profile-update-locked`. Kept literal.
  - zh `configs_exported` / `configs_imported` were labels ("导出配置") but are status messages -> 配置已导出 / 配置已导入.
- Placeholder spacing: fa/zh strings with `{count}`, `{name}`, `{id}`, `%1$d`, `%1$s` had no spaces around the value. Fixed in `routing_deleted`, `routing_imported`, `routing_import_clipboard_message`, `routing_default_proxy_server`, `routing_default_outbound_subtitle`, `configs_rule_summary`, `configs_new_name`, `configs_copy_name`. In zh, "%1$d路由规则" also lacked a counter; it is now "路由规则：%1$d".

Missing fa/zh translations filled (122 lines): `configs_rules_*` (add_condition, add_group, group_operator, logical_unsupported, type_or/and, any_of/all_of and their hints, word_or/and, summary_title/empty), `configs_per_app_desktop_notice`, `configs_import_file`, and all 45 `routing_table_*` keys.

## 2. Duplicates (same English, same meaning)

- "Routing rules": `routing_title` = `configs_rules_title`.
- "Name": `configs_name` = `routing_table_col_name` = `routing_table_name`.
- "Send to": `routing_table_col_route` = `routing_table_send_to`.
- "Delete": `routing_table_delete` vs the generic delete key in other batches.
- "Edit rule" / "Add rule" in the `configs_rules_*` set vs "Edit routing rule" / "Add routing rule" in the `routing_*` set (same screen purpose, different wording).
- Terminology split: `configs_*` call the object "configuration" and `routing_*` call it "route" (`routing_edit` / `routing_delete`: "Edit route" / "Delete route", while RU says "правило"). Left in EN.

## 3. Uncertain, for the product owner (EN left unchanged)

- `routing_table_processes_help`: "steam.exe. Not supported with TUN". I could not confirm this in code. The Desktop TUN runs hev-socks5-tunnel as a separate process; check whether process rules really don't work in TUN mode. fa/zh follow the EN text.
- `configs_dns_tun_dns_summary` says "Android TUN" and "Xray DoH/DoT settings", but the editor is also in shared Desktop UI. Also `RoscomRoutingJson.kt:164` says Xray provides no DoT or remote DoQ, while `configs_dns_summary` advertises "local DoQ" and the `configs_dns_protocol_dot` key exists. Decide whether to mention DoT/DoQ.
- Probably unused keys (no `Res.string.` / `R.string.` reference found, so their text can't be checked against behavior): `configs_dns_direct_fallback_proxy_summary` (the switch is shown without a summary), `configs_dns_proxy_summary`, `configs_dns_direct_summary`, `configs_dns_direct_domains_summary`, `configs_dns_hosts_summary`, `configs_dns_protocol_doh`, `configs_dns_protocol_dot`, `configs_dns_hosts_input`, `configs_hevtun_tcp_read_write_timeout`, `configs_fake_dns_ip_pool`, `configs_tun_mtu`, `routing_default_outbound_tag`, `routing_default_outbound_subtitle`, `routing_deleted`, `configs_activate`, `configs_raw`, `routing_rules_unapplied_title`. Patched only for fa/zh/ru errors; consider deleting. `configs_raw` (ru "raw view") was left untranslated for that reason.
- `configs_dns_domain_rule_input` and `configs_dns_hosts_input` keep their current fa/zh hint wording (the EN text is only a bare example, the fa/zh text is a labeled hint). Pick one style.
- `routing_domain_policy` ("Domain policy") and `routing_domain_strategy` ("Domain strategy") are two different screens (Android routing list header vs the Xray domainStrategy dropdown). zh now says 域名策略 and 域名解析策略 to keep them apart. Confirm that "policy" really means something different from "strategy" there.
- `routing_info_desc_android_limitations` points to "App List Mode in Settings". The settings title is "Proxy app list" (`proxy_app_list_title`); the name differs but I left it.

---

## Batch a6 report (248 keys checked, 83 patched)

## 1. Behavior mismatches (English/meaning changed)

- subscription_update_via_proxy_summary
  - Logic: app/.../SubscriptionUpdateUseCase.kt:281 (`useRunningProxy = group.updateViaProxy && proxyRunning`); desktop DesktopLocalProxyRuntime.kt:116.
  - Old text: "Use the local proxy for subscription requests" (ru: "Задействовать локальное ядро прокси...").
  - Code: the proxy is used only while the local proxy is running; otherwise the request goes direct.
  - New en: "Use the local proxy for subscription requests while it is running". ru/fa/zh updated to match.
- subscription_auto_override_rules_summary
  - Logic: shared Custom.overrideInboundAndDns (Custom.kt:19), set from group.autoOverrideRules (SubscriptionCatalogOperations.kt:164-166); XrayExportConfig.custom replaces the config's inbounds (and DNS/routing, same wording as proxy_editor_custom_override_inbound_dns_summary).
  - Old text: "Apply SKIPI routing rules to all configurations in the subscription". It named only routing, and the flag acts on custom JSON configs.
  - New en: "Use SKIPI inbound, DNS and routing rules instead of those in each custom JSON configuration of the subscription". Other languages updated.
- subscription_auto_override_rules (fa): "لغو خودکار قوانین" meant "automatic cancellation of rules". Now "بازنویسی خودکار قوانین".
- nav_configs (fa): was "تنظیمات", which is Settings. Now "پیکربندی‌ها".

Other fixes (translation and typo, no logic change):
- zh: wrong terms (暂停 for Timeout, 风俗 for Custom, 冲突元 for Clash Meta, 闪击X, 日志猫, 清爽…, 团体, 代理人, 光/黑暗的, 代替, 路线).
- zh: missing common_back and common_less; the stray space in error_vpn_permission_denied was removed.
- zh and zh-rCN now agree. Only connection_status_disconnected differed; both are now 已断开连接.
- fa: missing common_back and common_less added. Many placeholders were glued to words (`:%1$s`, `{count}` with no space) and are now spaced. The Persian note `url_schemes_note_desc` had a real newline instead of `\n`, as did zh; both fixed to `\n`.
- fa: transliterated or odd terms (سیاهههای مربوط, نور for Light, کاربر-عامل, کلش متا, کلید عصر) replaced with گزارش, روشن, User-Agent, Clash Meta, and Age key.
- ru: missing common_back added. "Еще" became "Ещё" and "Идет защищенное" became "Идёт защищённое" for ё consistency. The trailing space in app_icon_default was trimmed. subscription_user_agent_custom now says "Пользовательский", matching subscription_custom_user_agent. logs_output now says "Вывод логов". The HTTP warning no longer says "HTTP cleartext" or "злоумышленниками".

## 2. Duplicates / overlaps

- Same English text and meaning:
  - "Cancel" = common_cancel = resource_file_download_notification_cancel
  - "Update" = common_update = subscription_provider_update = app_update_install_action
  - "Delete subscription group" = deletion_confirmation_delete_subscription_group = subscription_delete_group
  - "Light", "Dark" and "Sunset" repeat across the app_icon_*, option_* and option_theme_* keys.
- Overlapping meaning, different text: subscription_ping_url ("URL for HTTP test") and subscription_ping_url_label ("Latency test URL"); subscription_ping_timeout ("Timeout"), subscription_ping_timeout_section ("Check timeout") and subscription_ping_timeout_label ("Response timeout").

## 3. Uncertain / owner decision (text left unchanged)

- Keys with no usage found in app/shared/desktopApp (likely dead):
  - Display keys: common_share, common_less, connection_status_connecting, connection_status_secured, connection_metric_session, connection_metric_latency, connection_metric_tunnel_memory, connection_metric_ram_format, deletion_confirmation_delete_route
  - Theme and option keys: option_theme_system/light/dark, theme_color_default/blue/green/violet/yellow/orange/rose/cyan
  - Subscription and User-Agent keys: subscription_ping_mode, subscription_ping_mode_summary, subscription_user_agent_v2rayng/clash_meta/flclash_x, subscription_move_left/right
  - I translated the dead ones that had defects anyway.
- Unused ping-sheet text: SubscriptionPingSettingsSheet.kt is not referenced anywhere. subscription_ping_timeout, subscription_ping_timeout_ms (500–60000 ms) and subscription_ping_timeout_invalid belong to that dead sheet. The live screen SkipiSubscriptionPingSettingsScreen uses a 1–30 s slider (coerceIn(1,30)) with a concurrency slider of 1–32. The validator SubscriptionPingSettingsRules still allows 500–60000 ms. Decide whether to delete these keys.
- subscription_ping_mode_summary describes TCP and HTTP, but the live screen shows plain "HTTP" and "TCP" boxes without it. Kept, translation fixed.
- Plurals: "%1$d servers", "%1$d days remaining", "{count} groups" and "Update every {interval} hours" give "1 servers", "1 days" and "every 1 hours". Russian has no plural forms either ("Серверов: N" is fine; "дн." is fine).
- url_scheme_flclash_desc says "FlClash", but the scheme is `flclashx://` and the User-Agent entry is "FlClash X". Should it say "FlClash X"?
- subscription_default_group ("Manual servers") vs subscription_manual_group ("Manual proxy server group"): both are shown for the manual group in different places. The names differ, so confirm that is intended.
- error_* strings: some end without a period (error_vpn_permission_denied). I left the punctuation as is.

---

## Batch a7 report (248 keys)

## 1. Behavior mismatches
- tools_dns_description (all langs): shared/core/.../features/tools/dnsleak/DnsLeakResultPolicy.kt:19-27. Old text said the observed network is compared with the HTTP exit and a path through a different network is a leak. Code compares COUNTRIES (resolver country vs exit country), and the summary strings already say "country". New text: country of that network is compared with the country of your exit.
- network_automation_action_switch_if_connected (en, ru): shared/core/.../NetworkAutomationPolicy.kt:130-135 and NetworkAutomationReconciler.kt:51-62. Old en "Connect only if VPN is active" / ru "Подключить, если включен" suggested it connects; the action never starts the VPN, it only switches the server when the VPN is already running. New: "Switch server only if VPN is on" / "Сменить сервер, только если VPN включён". (The UI shows it as "<label>: <server name>".)
- network_automation_no_wifi_detected (fa, zh): SettingsNetworkAutomationPage.kt:73 shows it when no SSID could be read (no Wi-Fi OR location service off). fa/zh said only "not connected to Wi-Fi". Aligned with English.
- skipi_imported_theme (ru): MainActivity.kt:463; the settings entry is settings_category_appearance = "Внешний вид", ru text said «Оформление» (that is only the section header). Changed to «Внешний вид».
- app_update_notification_channel_description (fa, zh): AppUpdateNotifier.kt shows available/downloading/ready notifications in this channel; fa/zh mentioned only download progress. Aligned with English.
- home_section_servers (zh): "节点" was the only use; whole zh file uses "服务器". Changed for consistency.
- tools_dns_* zh strings used "你/你的" while the rest of the file uses "您" (20 vs 4). Unified to "您" (tools_dns_exit_label, summary_unknown/leak/no_leak).
- Other consistency fixes: ru tools_ip_info_tunnel_vpn -> "VPN-туннель / прокси"; fa tools_ip_info_refresh -> "تازه‌سازی"; fa tools_dns_group_public -> "مبدل‌های عمومی" (matches "مبدل" used elsewhere); ru external_theme_confirm_message quotes -> «».

Missing translations filled (fa and zh/zh-rCN): app_update_check_failed, network_automation_wifi_rule_title, network_automation_wifi_permission_summary/_denied/_launcher_missing, tools_speed_ms, all 24 custom_themes_* keys, subscription_advanced, external_theme_confirm_message, skipi_imported_theme, skipi_imported_theme_existing, skipi_imported_theme_full. (ru already present for all.)

## 2. Duplicates (same English, same meaning)
- deletion_confirmation_delete_group == group_delete ("Delete group")
- onboarding_telegram_channel_title == onboarding_telegram_title ("Official Telegram Channel")
- tools_speed_stop == tools_dns_stop ("Stop")
- network_automation_save == custom_themes_save ("Save"); custom_themes_add / external_import_confirm_accept ("Add"); custom_themes_delete ("Delete") likely also exists elsewhere
- subscription_notify_on_expiry == subscription_expiry_notifications_section ("Expiry Notifications")
- tools_dns_start ("Run test") vs tools_speed_start ("Start test"): different wording, same role

## 3. Uncertain / needs product owner
- Unused keys (no Kotlin reference anywhere): tools_dns_running, tools_dns_no_leak, tools_dns_suspected_leak, tools_dns_unknown, tools_dns_resolvers_title, tools_dns_exit_info, tools_dns_server_system, tools_dns_server_public, tools_dns_resolver_entry. They also still use the old wording. Suggest deleting; left unchanged.
- network_automation_action_switch_server "Always connect server": it only starts the VPN when On-Demand VPN is on; with only Auto-switch it merely switches the server when the VPN is already running. "Always connect" may over-promise. Left unchanged.
- onboarding_telegram_feature_chat_* ("Ask questions, share configs, get help"): cannot verify the channel has a discussion chat. Unchanged.
- app_update_check_failed ("current update notice was kept"): wording is Android-oriented; the same text is shown on Desktop. Unchanged.
- tools_dns_exit_label / summaries use the loanword "exit" in ru; kept as the owner's terminology.
- network_automation_no_wifi_detected mentions "location service", which is Android-specific; Desktop shows the same text (DesktopSettingsScreen.kt:277). Unchanged.

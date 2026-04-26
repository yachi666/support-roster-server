# Contact Information API

## 文档定位

本专题定义公开 `contact-information` 功能的后端契约、访问边界、持久化结构与当前交付范围。它覆盖独立页面 `/contact-information` 的读能力，以及 `/contact-information/add` 提交时所依赖的写能力。

## 路由与访问边界

### 资源路径

- `GET /api/contact-information`
- `POST /api/contact-information`

### 访问策略

- 列表读取：**公开可读**，不要求登录
- 创建记录：**workspace admin only**，后端使用 `AuthContextService.requireAdmin()`

## 当前交付范围

- 支持团队联系信息列表
- 服务端关键字搜索
- 服务端分页
- 管理员创建记录

当前不包含：

- 编辑记录
- 删除记录
- 详情页

## 数据模型

### 主表

`support_team_contact`

- `team_name`
- `team_email`
- `xmatter_group`
- `gsd_group`
- `eim_id`
- `other_info`

### 子表

- `support_team_contact_tag`
- `support_team_contact_staff`
- `support_team_contact_link`

### 数据规则

- `team_name` 必填
- `team_email` 必填且唯一
- 至少一个 tag
- 至少一个 `staff_code`
- 所有 `staff_code` 必须能命中 `workspace_staff`
- `other_info` 以主表字段存储，响应时映射回 `label=Other` 的链接项

## 接口契约

### `GET /api/contact-information`

查询参数：

- `keyword`：可选
- `page`：必填，1-based
- `pageSize`：必填

搜索范围：

- 团队名
- 团队邮箱
- xMatter / GSD / EIM
- `other_info`
- tag
- staff code
- link label / url

排序规则：

- 默认按 `team_name` 升序，`id` 作为稳定次序

响应结构：

```json
{
  "items": [
    {
      "id": 1,
      "name": "Payments Core",
      "email": "payments-core@company.com",
      "xMatter": "XM-PAY-01",
      "gsd": "GSD-PAY-882",
      "eim": "EIM-9331",
      "roles": ["Upstream"],
      "staff": [
        {
          "id": "S-10492",
          "name": "Alex Chen",
          "email": "alex.c@company.com",
          "avatar": null
        }
      ],
      "links": [
        {
          "label": "Other",
          "url": "https://example.com/wiki"
        }
      ]
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1
}
```

### `POST /api/contact-information`

请求体：

```json
{
  "name": "Payments Core",
  "email": "payments-core@company.com",
  "xMatter": "XM-PAY-01",
  "gsd": "GSD-PAY-882",
  "eim": "EIM-9331",
  "roles": ["Upstream"],
  "staffIds": ["S-10492"],
  "links": [
    {
      "label": "Other",
      "url": "https://example.com/wiki"
    }
  ]
}
```

行为约束：

- 后端先做 admin 权限校验
- 写入主表与子表必须在一个事务内完成
- `label=Other` 的链接不落子表，写入 `other_info`
- 返回值与列表项结构保持一致

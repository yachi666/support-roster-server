# Source Excel 数据结构说明

## 文件信息

- **文件路径**: `roster.xlsx`
- **工作表数量**: 3
- **工作表名称**: 
  - Sheet1: 排班规则 (Shift Definitions)
  - Sheet2: 值班人员排班 (Staff Shifts)
  - Sheet3: 颜色定义 (Color Definitions)

---

## Sheet1: 排班规则

### 表头定义

| 列名 | 数据类型 | 说明 | 示例值 |
|------|----------|------|--------|
| `role_group` | String | 角色组名称 | `L1_China`, `AP_L2`, `EMEA_L2` |
| `code` | String | 班次代码 | `A`, `B`, `C`, `D`, `DS`, `NS`, `OC`, `BH`, `HoL` |
| `meaning` | String | 班次含义描述 | `00:00-07:00`, `Day Shift`, `Night Shift` |
| `start_time` | Time | 班次开始时间 | `00:00:00`, `09:30:00` |
| `end_time` | Time | 班次结束时间 | `07:00:00`, `18:30:00` |
| `timezone` | String | 时区代码 | `HKT`, `IST`, `INT` |
| `show_on_the_roster_page` | String | 是否在排班页显示 | `Y`, `N` |
| `remark` | String | 备注信息 | `Primary Incident Manager on the duty week` |

---

## Sheet2: 值班人员排班

### 表头定义

| 列名 | 数据类型 | 说明 | 示例值 |
|------|----------|------|--------|
| `name` | String | 员工姓名 | `test1`, `test2` |
| `staff_id` | Integer | 员工ID | `123`, `124`, `125` |
| `role_group` | String | 角色组名称 | `L1_China`, `AP_L2`, `EMEA_L2` |
| `region` | String | 地区 | `China`, `India` |
| `contact` | String | 联系方式 | - |
| `notes` | String | 备注信息 | - |
| `1-31` | String | 每日排班代码 | `A`, `B`, `HoL`, `NS` 等 |

---

## Sheet3: 颜色定义

### 表头定义

| 列名 | 数据类型 | 说明 | 示例值 |
|------|----------|------|--------|
| `code` | String | 班次代码 | `A`, `B`, `DS`, `NS` |
| `color_name` | String | 颜色名称 | `Gold`, `Orange`, `Red` |
| `rgb` | String | RGB值 | `255 215 0` |
| `hex` | String | 十六进制颜色值 | `#FFD700` |

---

## 角色组 (role_group) 分类

### L1 支持团队

| role_group | 说明 |
|------------|------|
| `L1_China` | L1 中国团队 |
| `L1_India` | L1 印度团队 |

### L2 支持团队

| role_group | 说明 |
|------------|------|
| `AP_L2` | 亚太区 L2 团队 |
| `EMEA_L2` | 欧洲/中东/非洲 L2 团队 |
| `MDP_L2` | MDP L2 团队 |

### 高级支持团队

| role_group | 说明 |
|------------|------|
| `AP_L2+` | 亚太区 L2+ 团队 |
| `AP_L3` | 亚太区 L3 团队 |

### 事件管理团队

| role_group | 说明 |
|------------|------|
| `Incident_Manager_China` | 中国事件管理团队 |
| `Incident_Manager_India` | 印度事件管理团队 |

### DevOps 团队

| role_group | 说明 |
|------------|------|
| `DevOps_China` | 中国 DevOps 团队 |
| `DevOps_India` | 印度 DevOps 团队 |

---

## 班次代码 (code) 定义

### L1 班次

| code | meaning | 说明 |
|------|---------|------|
| `A` | 00:00-07:00 | 通宵班 (凌晨) |
| `B` | 06:30-15:30 | 早班 |
| `C` | 08:00-17:00 | 正常班 (不显示在排班页) |
| `D` | 15:30-00:30 | 晚班 (跨天) |

### L2 团队班次

| code | meaning | 说明 |
|------|---------|------|
| `DS` | Day Shift | 日班 |
| `NS` | Night Shift | 夜班 |

### 特殊班次

| code | meaning | 说明 |
|------|---------|------|
| `OC` | Full Day Oncall Support | 全天待命支持 |
| `BH` | Business Hours | 工作时间 |
| `HoL` | Holiday or Leave | 假期或请假 (00:00-23:59) |

---

## 时区 (timezone) 说明

| timezone | 说明 |
|----------|------|
| `HKT` | 香港时间 (Hong Kong Time, UTC+8) |
| `IST` | 印度标准时间 (India Standard Time, UTC+5:30) |
| `INT` | 国际时间 (International Time, UTC) |

---

## 完整数据表

### Sheet1: 排班规则 真实数据 (来自Excel文件)

| role_group | code | meaning | start_time | end_time | timezone | show | remark |
|------------|------|---------|------------|----------|----------|------|--------|
| L1_China | A | 00:00-07:00 | 00:00:00 | 07:00:00 | HKT | Y | |
| L1_China | B | 06:30-15:30 | 06:30:00 | 15:30:00 | HKT | Y | |
| L1_China | C | 08:00 - 17:00 | 08:00:00 | 17:00:00 | HKT | N | |
| L1_China | D | 15:30-00:30 | 15:30:00 | 00:30:00 | HKT | Y | |
| L1_China | BH | Business Hours | 09:00:00 | 18:30:00 | HKT | N | Can move the start and end times back by one hour. |
| L1_China | HoL | Holiday or Leave | 00:00:00 | 23:59:00 | HKT | N | |
| L1_India | A | 00:00-07:00 | 00:00:00 | 07:00:00 | HKT | Y | |
| L1_India | B | 06:30-15:30 | 06:30:00 | 15:30:00 | HKT | Y | |
| L1_India | C | 08:00 - 17:00 | 08:00:00 | 17:00:00 | HKT | N | |
| L1_India | D | 15:30-00:30 | 15:30:00 | 00:30:00 | HKT | Y | |
| L1_India | BH | Business Hours | 09:00:00 | 18:30:00 | IST | N | Can move the start and end times back by one hour. |
| L1_India | HoL | Holiday or Leave | 00:00:00 | 23:59:00 | HKT | N | |
| AP_L2 | DS | Day Shift | 09:30:00 | 18:30:00 | HKT | Y | |
| AP_L2 | NS | Night Shift | 18:30:00 | 09:30:00 | HKT | Y | |
| AP_L2 | BH | Business Hours | 09:00:00 | 18:30:00 | HKT | N | |
| AP_L2 | HoL | Holiday or Leave | 00:00:00 | 23:59:00 | HKT | N | |
| EMEA_L2 | DS | Day Shift | 07:00:00 | 19:00:00 | IST | Y | |
| EMEA_L2 | NS | Night Shift | 19:00:00 | 07:00:00 | IST | Y | |
| EMEA_L2 | BH | Business Hours | 09:00:00 | 19:00:00 | IST | N | |
| EMEA_L2 | HoL | Holiday or Leave | 00:00:00 | 23:59:00 | HKT | N | |
| MDP_L2 | DS | Day Shift | 07:00:00 | 19:00:00 | HKT | Y | |
| MDP_L2 | NS | Night Shift | 19:00:00 | 07:00:00 | HKT | Y | |
| MDP_L2 | BH | Business Hours | 09:00:00 | 19:00:00 | HKT | N | |
| MDP_L2 | HoL | Holiday or Leave | 00:00:00 | 23:59:00 | HKT | N | |
| Incident_Manager_China | OC | Full Day Oncall Support | 00:00:00 | 23:59:00 | HKT | Y | Primary Incident Manager on the duty week |
| Incident_Manager_China | HoL | Holiday or Leave | 00:00:00 | 23:59:00 | HKT | N | |
| Incident_Manager_China | BH | Business Hours | 09:00:00 | 18:30:00 | HKT | N | |
| Incident_Manager_India | OC | Full Day Oncall Support | 00:00:00 | 23:59:00 | IST | Y | Primary Incident Manager on the duty week |
| Incident_Manager_India | HoL | Holiday or Leave | 00:00:00 | 23:59:00 | IST | N | |
| Incident_Manager_India | BH | Business Hours | 09:00:00 | 18:30:00 | IST | N | |
| DevOps_China | OC | Full Day Oncall Support | 00:00:00 | 23:59:00 | HKT | Y | |
| DevOps_China | HoL | Holiday or Leave | 00:00:00 | 23:59:00 | HKT | N | |
| DevOps_China | BH | Business Hours | 09:00:00 | 18:30:00 | HKT | N | |
| DevOps_India | OC | Full Day Oncall Support | 00:00:00 | 23:59:00 | HKT | Y | |
| DevOps_India | HoL | Holiday or Leave | 00:00:00 | 23:59:00 | HKT | N | |
| DevOps_India | BH | Business Hours | 09:00:00 | 18:30:00 | HKT | N | |
| AP_L2+ | OC | Full Day Oncall Support | 00:00:00 | 23:59:00 | HKT | N | |
| AP_L2+ | HoL | Holiday or Leave | 00:00:00 | 23:59:00 | HKT | N | |
| AP_L2+ | BH | Business Hours | 09:00:00 | 18:30:00 | HKT | N | |
| AP_L3 | OC | Full Day Oncall Support | 00:00:00 | 23:59:00 | HKT | N | |
| AP_L3 | HoL | Holiday or Leave | 00:00:00 | 23:59:00 | HKT | N | |
| AP_L3 | BH | Business Hours | 09:00:00 | 18:30:00 | HKT | N | |

### Sheet2: 值班人员排班 真实数据

| name | staff_id | role_group | region | contact | notes | 1 | 2 | 3-31 |
|------|----------|------------|--------|---------|-------|---|---|------|
| test1 | 123 | L1_China | China | | | A | HoL | |
| test2 | 124 | L1_India | India | | | B | HoL | |
| test3 | 125 | AP_L2 | China | | | NS | HoL | |
| test4 | 126 | AP_L2 | China | | | BH | HoL | |
| test5 | 127 | EMEA_L2 | India | | | BH | HoL | |
| test6 | 128 | EMEA_L2 | India | | | NS | HoL | |
| test7 | 129 | Incident_Manager_China | China | | | OC | HoL | |
| test8 | 130 | L1_China | China | | | HoL | A | |
| test9 | 131 | L1_China | China | | | HoL | B | |

> 注: 列 3-31 表示每月的日期，值为空表示该日无排班安排。

### Sheet3: 颜色定义 真实数据

| code | color_name | rgb | hex |
|------|------------|-----|-----|
| A | Orange | 255 165 0 | #FFA500 |
| B | DarkOrange | 255 140 0 | #FF8C00 |
| D | Coral | 255 127 80 | #FF7F50 |
| OC | Tomato | 255 99 71 | #FF6347 |
| DS | OrangeRed | 255 69 0 | #FF4500 |
| NS | Red | 255 0 0 | #FF0000 |
| C | SeaGreen1 | 84 255 159 | #54FF9F |
| BH | SeaGreen2 | 78 238 148 | #4EEE94 |
| HoL | LightGray | 211 211 211 | #D3D3D3 |

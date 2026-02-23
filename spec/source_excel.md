# Source Excel 数据结构说明

## 文件信息

- **文件路径**: `roster.xlsx`
- **工作表数量**: 1
- **工作表名称**: Sheet1

---

## 数据结构

### 表头定义

| 列名 | 数据类型 | 说明 | 示例值 |
|------|----------|------|--------|
| `role_group` | String | 角色组名称 | `L1_China`, `AP_L2`, `EMEA_L2` |
| `code` | String | 班次代码 | `A`, `B`, `C`, `D`, `DS`, `NS`, `OC`, `BH`, `HoL` |
| `meaning` | String | 班次含义描述 | `00:00-07:00`, `Day Shift`, `Night Shift` |
| `start_time` | Time | 班次开始时间 | `00:00:00`, `09:30:00` |
| `end_time` | Time | 班次结束时间 | `07:00:00`, `18:30:00` |
| `timezone` | String | 时区代码 | `HKT`, `INT` |
| `staff_id` | Integer | 员工ID | `123`, `124`, `125` |
| `show_on_the_roster_page` | String | 是否在排班页显示 | `Y`, `N` |
| `remark` | String | 备注信息 | `Primary Incident Manager on the duty week` |

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

### 常规班次

| code | meaning | 说明 |
|------|---------|------|
| `A` | 00:00-07:00 | 通宵班 (凌晨) |
| `B` | 06:30-15:30 | 早班 |
| `C` | 08:00-17:00 | 正常班 |
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
| `BH` | Business Hours | 工作时间 (09:00-18:30) |
| `HoL` | Holiday or Leave | 假期或请假 (00:00-23:59) |

---

## 时区 (timezone) 说明

| timezone | 说明 |
|----------|------|
| `HKT` | 香港时间 (Hong Kong Time, UTC+8) |
| `INT` | 国际时间 (International Time) |

---

## 完整数据表

### L1_China

| code | meaning | start_time | end_time | timezone | staff_id | show | remark |
|------|---------|------------|----------|----------|----------|------|--------|
| A | 00:00-07:00 | 00:00:00 | 07:00:00 | HKT | 123 | Y | |
| B | 06:30-15:30 | 06:30:00 | 15:30:00 | HKT | 124 | Y | |
| C | 08:00-17:00 | 08:00:00 | 17:00:00 | HKT | 125 | N | |
| D | 15:30-00:30 | 15:30:00 | 00:30:00 | HKT | 126 | Y | |
| BH | Business Hours | 09:00:00 | 18:30:00 | HKT | 127 | N | Can move the start and end times back by one hour. |
| HoL | Holiday or Leave | 00:00:00 | 23:59:00 | HKT | 128 | N | |

### L1_India

| code | meaning | start_time | end_time | timezone | staff_id | show | remark |
|------|---------|------------|----------|----------|----------|------|--------|
| A | 00:00-07:00 | 00:00:00 | 07:00:00 | HKT | 129 | Y | |
| B | 06:30-15:30 | 06:30:00 | 15:30:00 | HKT | 130 | Y | |
| C | 08:00-17:00 | 08:00:00 | 17:00:00 | HKT | 131 | N | |
| D | 15:30-00:30 | 15:30:00 | 00:30:00 | HKT | 132 | Y | |
| BH | Business Hours | 09:00:00 | 18:30:00 | INT | 133 | N | Can move the start and end times back by one hour. |
| HoL | Holiday or Leave | 00:00:00 | 23:59:00 | HKT | 134 | N | |

### AP_L2

| code | meaning | start_time | end_time | timezone | staff_id | show | remark |
|------|---------|------------|----------|----------|----------|------|--------|
| DS | Day Shift | 09:30:00 | 18:30:00 | HKT | 135 | Y | |
| NS | Night Shift | 18:30:00 | 09:30:00 | HKT | 136 | Y | |
| BH | Business Hours | 09:00:00 | 18:30:00 | HKT | 137 | N | |
| HoL | Holiday or Leave | 00:00:00 | 23:59:00 | HKT | 138 | N | |

### EMEA_L2

| code | meaning | start_time | end_time | timezone | staff_id | show | remark |
|------|---------|------------|----------|----------|----------|------|--------|
| DS | Day Shift | 07:00:00 | 19:00:00 | INT | 139 | Y | |
| NS | Night Shift | 19:00:00 | 07:00:00 | INT | 140 | Y | |
| BH | Business Hours | 09:00:00 | 19:00:00 | INT | 141 | N | |
| HoL | Holiday or Leave | 00:00:00 | 23:59:00 | HKT | 142 | N | |

### MDP_L2

| code | meaning | start_time | end_time | timezone | staff_id | show | remark |
|------|---------|------------|----------|----------|----------|------|--------|
| DS | Day Shift | 07:00:00 | 19:00:00 | HKT | 143 | Y | |
| NS | Night Shift | 19:00:00 | 07:00:00 | HKT | 144 | Y | |
| BH | Business Hours | 09:00:00 | 19:00:00 | HKT | 145 | N | |
| HoL | Holiday or Leave | 00:00:00 | 23:59:00 | HKT | 146 | N | |

### Incident_Manager_China

| code | meaning | start_time | end_time | timezone | staff_id | show | remark |
|------|---------|------------|----------|----------|----------|------|--------|
| OC | Full Day Oncall Support | 00:00:00 | 23:59:00 | HKT | 147 | Y | Primary Incident Manager on the duty week |
| HoL | Holiday or Leave | 00:00:00 | 23:59:00 | HKT | 148 | N | |
| BH | Business Hours | 09:00:00 | 18:30:00 | HKT | 149 | N | |

### Incident_Manager_India

| code | meaning | start_time | end_time | timezone | staff_id | show | remark |
|------|---------|------------|----------|----------|----------|------|--------|
| OC | Full Day Oncall Support | 00:00:00 | 23:59:00 | INT | 150 | Y | Primary Incident Manager on the duty week |
| HoL | Holiday or Leave | 00:00:00 | 23:59:00 | INT | 151 | N | |
| BH | Business Hours | 09:00:00 | 18:30:00 | INT | 152 | N | |

### DevOps_China

| code | meaning | start_time | end_time | timezone | staff_id | show | remark |
|------|---------|------------|----------|----------|----------|------|--------|
| OC | Full Day Oncall Support | 00:00:00 | 23:59:00 | HKT | 153 | Y | |
| HoL | Holiday or Leave | 00:00:00 | 23:59:00 | HKT | 154 | N | |
| BH | Business Hours | 09:00:00 | 18:30:00 | HKT | 155 | N | |

### DevOps_India

| code | meaning | start_time | end_time | timezone | staff_id | show | remark |
|------|---------|------------|----------|----------|----------|------|--------|
| OC | Full Day Oncall Support | 00:00:00 | 23:59:00 | HKT | 156 | Y | |
| HoL | Holiday or Leave | 00:00:00 | 23:59:00 | HKT | 157 | N | |
| BH | Business Hours | 09:00:00 | 18:30:00 | HKT | 158 | N | |

### AP_L2+

| code | meaning | start_time | end_time | timezone | staff_id | show | remark |
|------|---------|------------|----------|----------|----------|------|--------|
| OC | Full Day Oncall Support | 00:00:00 | 23:59:00 | HKT | 159 | N | |
| HoL | Holiday or Leave | 00:00:00 | 23:59:00 | HKT | 160 | N | |
| BH | Business Hours | 09:00:00 | 18:30:00 | HKT | 161 | N | |

### AP_L3

| code | meaning | start_time | end_time | timezone | staff_id | show | remark |
|------|---------|------------|----------|----------|----------|------|--------|
| OC | Full Day Oncall Support | 00:00:00 | 23:59:00 | HKT | 162 | N | |
| HoL | Holiday or Leave | 00:00:00 | 23:59:00 | HKT | 163 | N | |
| BH | Business Hours | 09:00:00 | 18:30:00 | HKT | 164 | N | |

---

## 颜色配置 (附加信息)

Excel 文件末尾包含班次颜色配置信息：

| code | 颜色名称 | RGB | 16进制 |
|------|----------|-----|--------|
| A | Orange | 255,165,0 | #FFA500 |
| B | DarkOrange | 255,140,0 | #FF8C00 |
| D | Coral | 255,127,80 | #FF7F50 |
| OC | Tomato | 255,99,71 | #FF6347 |
| DS | OrangeRed | 255,69,0 | #FF4500 |
| NS | Red | 255,0,0 | #FF0000 |
| C | SeaGreen1 | 84,255,159 | #54FF9F |
| BH | SeaGreen2 | 78,238,148 | #4EEE94 |
| HoL | LightGray | 211,211,211 | #D3D3D3 |

---

## 数据统计

- **总行数**: 43 行有效数据 (不含表头和颜色配置)
- **角色组数量**: 11 个
- **班次类型**: 9 种 (A, B, C, D, DS, NS, OC, BH, HoL)
- **时区类型**: 2 种 (HKT, INT)
- **staff_id 范围**: 123 - 164

---

## 注意事项

1. **跨天班次**: 部分班次如 `D` (15:30-00:30) 和 `NS` (Night Shift) 的结束时间小于开始时间，表示跨天班次
2. **show_on_the_roster_page**: `Y` 表示该班次需要在排班页面显示，`N` 表示不显示
3. **颜色配置**: 文件末尾的颜色配置用于前端展示，非排班数据

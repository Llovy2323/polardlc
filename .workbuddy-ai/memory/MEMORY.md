# MEMORY.md — проект SNILL / Polar DLC

## Что это

Fabric мод-чит-клиент для Minecraft Java. Исходник взят с версии **1.21.4**.
Цель Короля: сделать на его базе свой клиент уровня Wexside / Nursultan / Wild.

## Решения

- **Целевая версия первого порта — 1.21.11.** Выбрано 05.09.2026 после сравнения с 26.2.
  Причина: 1.21.11 — последняя обфусцированная, Yarn ещё жив, имена в коде не меняются,
  Java та же (21), Baritone есть форками. 26.x требует переезда на Mojang Mappings
  (переименование всех 52k строк) + Java 25 + Baritone под вопросом.
- **26.x — вторым этапом**, после того как порт встанет и код будет развязан с версией.
- Старт работ отложен до явной команды Короля.

## Проверенный тулчейн под 1.21.11

```
minecraft 1.21.11
yarn       1.21.11+build.6   (stable)
intermediary 1.21.11         (stable)
loader     0.19.5            (stable)
fabric-api 0.141.6+1.21.11
loom       1.14+   (1.17.20 стабильный, 1.18.0-alpha в разработке)
mixinextras 0.5.5
java       21 — апгрейд не нужен
```

Baritone под 1.21.11: форки `0Mattias/cheesecake`, `Nevarielle/baritone-1.21.11`.
Официального релиза cabaletta под 1.21.11 нет.

## Структура кода

- `api/` — event bus (EventInvoker/EventLink), storages, utils (render, math, combat, rpc, script/LuaJ)
- `client/modules/impl/` — combat / movement / player / render / misc, ~80 модулей
- `client/ui/` — MenuPanel, clickgui, altmanager, space
- `mixin/` — 56 миксинов, главная боль при порте
- `mods/` — кастомные партиклы (maseffects, particular)

Объём: 351 java-файл, ~52 000 строк.

## Мёртвый код (выпилить при порте)

- `ru.virtuoz/client/User.java` — JNI-методы авторизации, **вызовов в коде нет**
- `ru.virtuoz/convert/Convert.java` — аннотации-маркеры обфускатора, на компиляцию не влияют

## Грабли, которые уже известны

- `Snill.java` использует `WorldRenderEvents.START` — Fabric этот API убирал и вернул с
  новым контрактом в 1.21.10. При порте переписывать.
- Рендер-стек сидит на `ShaderProgram` / `Framebuffer` / `GlUniform` (RenderUtils, 1657 строк).
- Подозрительные миксины: `ItemModelManagerMixin`, `SelectItemModelMixin`,
  `LivingEntityRenderStateMixin`, `TextVisitFactoryMixin`, `DrawContextNameProtectMixin`.

## Зоны визуала (для этапа после порта)

- `client/ui/clickgui/` — ClickGuiState / Renderer / SettingRenderer / InputHandler / ThemeSelector
- `client/ui/MenuPanel.java` — главное меню, Screen, анимации
- `api/utils/animation/` — AnimationUtils, Easings
- `api/storages/implement/ThemeStorage.java`, `helpertstorages/Theme.java` — темы
- `api/utils/render/fonts/` — MSDF + TTF
- `api/utils/draggable/Draggable.java` — HUD-виджеты
- `api/utils/render/blur/`, `glow/` — пост-эффекты
- `client/modules/impl/render/base/implement/` — TargetHud, Cooldowns

## Правило работы

Порт сначала до состояния «компилируется + логически корректно».
Рантайм-проверку в игре делает Король, я чиню по краш-логам.
Визуал переписывать **после** порта, не параллельно — иначе двойная работа.

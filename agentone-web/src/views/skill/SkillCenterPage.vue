<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="page-head">
      <div>
        <h1 class="page-title">Skill 中心</h1>
        <p class="page-desc" style="margin-bottom: 0;">市场、销售、客服、人事、财务……每个工种都能找到趁手的 AI 技能，或把团队经验沉淀成新技能。</p>
      </div>
      <div class="toolbar-actions">
        <router-link class="btn-gradient" to="/skills/create">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          创建技能
        </router-link>
      </div>
    </div>

    <!-- Tabs：广场 / 我的技能 -->
    <div class="tabs" role="tablist">
      <button class="tab" :class="{ active: view === 'plaza' }" role="tab" @click="switchView('plaza')">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z"/></svg>
        技能广场
      </button>
      <button class="tab" :class="{ active: view === 'mine' }" role="tab" @click="switchView('mine')">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M2 7l10-5 10 5-10 5z"/><path d="M2 17l10 5 10-5M2 12l10 5 10-5"/></svg>
        我的技能 <span class="count">{{ mineTotal }}</span>
      </button>
    </div>

    <!-- ===================== 广场视图 ===================== -->
    <template v-if="view === 'plaza'">
      <div class="plaza-toolbar">
        <div class="search-box">
          <svg class="search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            v-model="plazaSearchInput"
            class="search-input"
            placeholder="搜索技能：名称、用途、关键词…"
          />
          <button v-if="plazaSearchInput" class="search-clear" @click="plazaSearchInput = ''">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>
      </div>

      <!-- 工种导航 -->
      <div class="chips">
        <button
          v-for="c in CATS"
          :key="c"
          class="chip"
          :class="{ active: activeCat === c }"
          @click="setCat(c)"
        >
          <span v-if="CAT_ICONS[c]" class="chip-icon" v-html="ICONS[CAT_ICONS[c]]"></span>{{ c }}
        </button>
      </div>

      <!-- 官方精选（无搜索、全部工种时才展示） -->
      <template v-if="officialFeatured.length > 0 && !plazaKeyword && activeCat === '全部'">
        <div class="section-title">官方精选 <span class="hint">开箱即用，覆盖常见工种场景</span></div>
        <div class="skill-grid">
          <div
            v-for="s in officialFeatured"
            :key="'f-' + s.id"
            class="skill-card"
            role="button"
            tabindex="0"
            @click="openDrawer(s)"
            @keydown.enter="openDrawer(s)"
          >
            <div class="skill-card-top">
              <div class="skill-icon" :style="iconStyle(s)" v-html="ICONS[iconOf(s)]"></div>
              <div class="skill-card-title">
                <div class="skill-name">{{ s.name }}</div>
                <div class="skill-author">{{ authorOf(s) }}</div>
              </div>
            </div>
            <div class="skill-tagline">{{ s.description || '暂无描述' }}</div>
            <div class="skill-card-foot">
              <span class="badge badge-cat">{{ s.category || '其他' }}</span>
              <span class="badge" :class="sourceBadgeOf(s).cls">{{ sourceBadgeOf(s).text }}</span>
              <span class="spacer"></span>
              <span v-if="s.callCount" class="installs">{{ s.callCount }} 次调用</span>
            </div>
          </div>
        </div>
      </template>

      <div v-if="plazaSkills.length > 0" class="section-title">
        {{ activeCat === '全部' ? '全部技能' : `${activeCat} · ${plazaSkills.length} 个技能` }}
      </div>
      <div v-if="plazaSkills.length > 0" class="skill-grid">
        <div
          v-for="s in plazaSkills"
          :key="s.id"
          class="skill-card"
          role="button"
          tabindex="0"
          @click="openDrawer(s)"
          @keydown.enter="openDrawer(s)"
        >
          <div class="skill-card-top">
            <div class="skill-icon" :style="iconStyle(s)" v-html="ICONS[iconOf(s)]"></div>
            <div class="skill-card-title">
              <div class="skill-name">{{ s.name }}</div>
              <div class="skill-author">{{ authorOf(s) }}</div>
            </div>
          </div>
          <div class="skill-tagline">{{ s.description || '暂无描述' }}</div>
          <div class="skill-card-foot">
            <span class="badge badge-cat">{{ s.category || '其他' }}</span>
            <span class="badge" :class="sourceBadgeOf(s).cls">{{ sourceBadgeOf(s).text }}</span>
            <span v-if="s.actionType" class="badge badge-script">执行需确认</span>
            <span class="spacer"></span>
            <span v-if="s.callCount" class="installs">{{ s.callCount }} 次调用</span>
          </div>
        </div>
      </div>

      <!-- 广场空态 -->
      <div v-if="!plazaLoading && plazaSkills.length === 0" class="empty">
        <div class="empty-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
        </div>
        <div class="empty-title">没有找到相关技能</div>
        <div class="empty-desc">换个关键词试试，或按工种浏览；也可以把团队经验沉淀成一个新技能。</div>
        <div class="empty-actions">
          <button class="btn-secondary-custom" @click="clearPlazaFilters">清空筛选</button>
          <router-link class="btn-gradient" to="/skills/create">创建技能</router-link>
        </div>
      </div>
    </template>

    <!-- ===================== 我的技能视图 ===================== -->
    <template v-else>
      <div class="plaza-toolbar">
        <div class="search-box">
          <svg class="search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            v-model="mineKeywordInput"
            class="search-input"
            placeholder="搜索我的技能…"
          />
          <button v-if="mineKeywordInput" class="search-clear" @click="mineKeywordInput = ''">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>
      </div>

      <div v-if="skills.length > 0" class="list-wrap">
        <div class="list-head">
          <div>技能</div><div>类型</div><div>工种</div><div>状态</div><div>调用次数</div><div style="text-align:right;">操作</div>
        </div>
        <div v-for="s in skills" :key="s.id" class="skill-row">
          <div class="row-skill" @click="openDrawer(s)">
            <div class="skill-icon" :style="iconStyle(s)" v-html="ICONS[iconOf(s)]"></div>
            <div style="min-width:0;">
              <div class="row-name">{{ s.name }}</div>
              <div class="row-sub">
                <span class="badge" :class="sourceBadgeOf(s).cls">{{ sourceBadgeOf(s).text }}</span>
                <span v-if="s.version" class="mono">v{{ s.version }}</span>
              </div>
            </div>
          </div>
          <div><span class="badge" :class="typeBadge(s.type)">{{ typeLabel(s.type) }}</span></div>
          <div class="row-stat">{{ s.category || '—' }}</div>
          <div>
            <label
              v-if="!isSystemManaged(s)"
              class="toggle"
              :title="toggleTitle(s)"
            >
              <input
                type="checkbox"
                :checked="isEnabled(s)"
                @change="(e: Event) => handleToggle(s, (e.target as HTMLInputElement).checked)"
              />
              <span class="track"></span>
            </label>
            <span v-else class="managed-tag">系统托管</span>
          </div>
          <div class="row-stat">
            {{ s.callCount != null ? `${s.callCount} 次` : '—' }}
            <small>{{ isEnabled(s) ? '' : '已停用' }}</small>
          </div>
          <div class="row-actions">
            <button class="icon-btn" title="试一试" @click="openDrawer(s, true)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 3l14 9-14 9V3z"/></svg>
            </button>
            <template v-if="isUserType(s.type)">
              <button class="icon-btn" title="编辑" @click="openEditModal(s)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 013 3L7 19l-4 1 1-4z"/></svg>
              </button>
              <button class="icon-btn" title="导出" @click="handleExport(s)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
              </button>
              <button class="icon-btn danger" title="删除" @click="deleteTarget = s">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
              </button>
            </template>
            <span v-else class="managed-hint">系统托管</span>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="mineTotal > pageSize" class="pagination-wrap">
        <n-pagination :page="page" :page-size="pageSize" :item-count="mineTotal" @update:page="handlePageChange" />
      </div>

      <!-- 我的技能空态 -->
      <div v-if="!loading && skills.length === 0" class="empty">
        <div class="empty-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M2 7l10-5 10 5-10 5z"/><path d="M2 17l10 5 10-5M2 12l10 5 10-5"/></svg>
        </div>
        <div class="empty-title">{{ mineKeyword ? '没有匹配的技能' : '还没有技能' }}</div>
        <div class="empty-desc">{{ mineKeyword ? '换个关键词试试，或清除搜索条件。' : '从技能广场安装现成技能，或把团队经验创建成第一个技能。' }}</div>
        <div class="empty-actions">
          <button v-if="mineKeyword" class="btn-secondary-custom" @click="mineKeywordInput = ''">清除搜索</button>
          <button v-else class="btn-secondary-custom" @click="switchView('plaza')">去广场看看</button>
          <router-link class="btn-gradient" to="/skills/create">创建技能</router-link>
        </div>
      </div>
    </template>

    <!-- ===================== 详情抽屉 ===================== -->
    <Teleport to="body">
      <div class="overlay" :class="{ show: drawerOpen }" @click="closeDrawer"></div>
      <aside v-if="drawerSkill" class="drawer" :class="{ show: drawerOpen }" aria-hidden="false">
        <div class="drawer-head">
          <button class="drawer-close" aria-label="关闭" @click="closeDrawer">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6L6 18M6 6l12 12"/></svg>
          </button>
          <div class="drawer-title-row">
            <div class="drawer-icon" :style="iconStyle(drawerSkill)" v-html="ICONS[iconOf(drawerSkill)]"></div>
            <div>
              <div class="drawer-name">{{ drawerSkill.name }}</div>
              <div class="drawer-badges">
                <span class="badge badge-cat">{{ drawerSkill.category || '其他' }}</span>
                <span class="badge" :class="sourceBadgeOf(drawerSkill).cls">{{ sourceBadgeOf(drawerSkill).text }}</span>
                <span v-if="drawerSkill.actionType" class="badge badge-script">执行需确认</span>
                <span v-if="isEnabled(drawerSkill)" class="badge badge-on">已启用</span>
                <span v-else-if="!isSystemManaged(drawerSkill)" class="badge badge-custom">未启用</span>
              </div>
            </div>
          </div>
        </div>

        <div class="drawer-body">
          <div class="drawer-section">
            <div class="drawer-label">它能做什么</div>
            <div class="drawer-desc">{{ drawerSkill.description || '暂无描述' }}</div>
          </div>

          <!-- 内容型技能：展示指令内容 -->
          <div v-if="promptContent" class="drawer-section">
            <div class="drawer-label">指令内容</div>
            <pre class="prompt-content">{{ promptContent }}</pre>
          </div>

          <!-- 技能包文件树（导入的技能包） -->
          <div v-if="packageFiles.length > 0" class="drawer-section">
            <div class="drawer-label">技能包文件</div>
            <div class="file-tree">
              <div v-for="f in packageFiles" :key="f.path" class="file-node">
                <span class="file-node-icon" v-html="ICONS[fileIconOf(f.kind)]"></span>
                {{ f.path }}
                <span class="f-type">{{ fileKindLabel(f.kind) }}</span>
              </div>
            </div>
            <div class="file-tree-note">注：脚本文件仅随包存储与分发，服务端不执行。</div>
          </div>

          <!-- 试一试 -->
          <div class="drawer-section">
            <div class="drawer-label">试一试</div>
            <div class="try-panel" :class="{ open: tryOpen }">
              <div class="try-head" @click="tryOpen = !tryOpen">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 3l14 9-14 9V3z"/></svg>
                运行一次，看看效果
                <svg class="chev" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 9l6 6 6-6"/></svg>
              </div>
              <div class="try-body">
                <template v-if="tryFields.length > 0">
                  <div v-for="f in tryFields" :key="f.key" class="field">
                    <label>{{ f.key }}<span v-if="f.required" class="req"> *</span></label>
                    <textarea
                      v-if="f.type === 'object' || f.type === 'array' || f.type === 'textarea'"
                      v-model="tryValues[f.key]"
                      rows="3"
                      :placeholder="f.description || ''"
                    ></textarea>
                    <select v-else-if="f.type === 'boolean'" v-model="tryValues[f.key]">
                      <option value="true">true</option>
                      <option value="false">false</option>
                    </select>
                    <select v-else-if="f.options" v-model="tryValues[f.key]">
                      <option value="" disabled>请选择</option>
                      <option v-for="opt in f.options" :key="opt" :value="opt">{{ opt }}</option>
                    </select>
                    <input v-else v-model="tryValues[f.key]" :placeholder="f.description || ''" />
                    <div v-if="f.description && f.type !== 'object' && f.type !== 'array'" class="field-hint">{{ f.description }}</div>
                  </div>
                </template>
                <div v-else class="try-no-params">该技能无需参数，直接运行即可。</div>

                <!-- 动作型：草稿确认 -->
                <div v-if="tryState === 'confirm'" class="try-confirm">
                  <div class="try-confirm-title">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                    该技能会执行真实操作，请确认参数
                  </div>
                  <pre class="try-confirm-params">{{ JSON.stringify(draftParams, null, 2) }}</pre>
                </div>

                <!-- 执行结果 -->
                <div v-if="tryState === 'result' && tryResult" class="try-result show" :class="tryResult.success ? 'ok' : 'fail'">
                  <template v-if="tryResult.success">✓ 运行成功<pre>{{ formatJson(tryResult.data) }}</pre></template>
                  <template v-else>✗ 运行失败：{{ tryResult.errorMessage }}</template>
                  <div class="try-meta">
                    耗时 {{ tryResult.durationMs ?? '-' }}ms · 已写入调用审计
                    <span v-if="tryResult.traceId"> · traceId: {{ tryResult.traceId }}</span>
                  </div>
                </div>

                <div class="try-actions">
                  <button v-if="tryState !== 'confirm'" class="btn-gradient btn-sm" :disabled="invoking" @click="runTry">
                    <span v-if="invoking" class="btn-spinner"></span>
                    {{ invoking ? '运行中...' : '运行' }}
                  </button>
                  <template v-else>
                    <button class="btn-gradient btn-sm" :disabled="invoking" @click="confirmTry">
                      <span v-if="invoking" class="btn-spinner"></span>
                      {{ invoking ? '执行中...' : '确认执行' }}
                    </button>
                    <button class="btn-secondary-custom btn-sm" :disabled="invoking" @click="tryState = 'idle'">取消</button>
                  </template>
                </div>
                <div class="adv-debug">
                  <a href="#" @click.prevent="openDebug(drawerSkill)">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2v4M12 18v4M4.9 4.9l2.9 2.9M16.2 16.2l2.9 2.9M2 12h4M18 12h4M4.9 19.1l2.9-2.9M16.2 7.8l2.9-2.9"/></svg>
                    高级调试（参数预检 / 执行计划）
                  </a>
                </div>
              </div>
            </div>
          </div>

          <!-- 技能信息 -->
          <div class="drawer-section">
            <div class="drawer-label">技能信息</div>
            <div class="meta-grid">
              <div class="meta-item"><div class="k">版本</div><div class="v">{{ drawerSkill.version || '—' }}</div></div>
              <div class="meta-item"><div class="k">添加时间</div><div class="v">{{ formatDate(drawerSkill.installedAt) }}</div></div>
              <div class="meta-item"><div class="k">调用次数</div><div class="v">{{ drawerSkill.callCount != null ? drawerSkill.callCount + ' 次' : '—' }}</div></div>
              <div class="meta-item"><div class="k">来源</div><div class="v">{{ authorOf(drawerSkill) }}</div></div>
            </div>
          </div>
        </div>

        <div class="drawer-foot">
          <button v-if="isUserType(drawerSkill.type)" class="btn-secondary-custom" style="flex:0 0 auto;" @click="openEditModal(drawerSkill)">编辑</button>
          <div class="agent-picker">
            <div class="agent-menu" :class="{ show: agentMenuOpen }">
              <div v-if="agents.length === 0" class="agent-empty">当前工作空间还没有 Agent</div>
              <button v-for="a in agents" :key="a.id" class="agent-opt" @click="enableToAgent(a.id, a.name)">
                <span class="ao-icon">
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="9" r="3"/><circle cx="8" cy="16" r="2"/><circle cx="16" cy="16" r="2"/><path d="M12 12v2M9.2 14.5l3 3M14.8 14.5l-3 3"/></svg>
                </span>
                <span>{{ a.name }}<small>{{ a.description || '暂无描述' }}</small></span>
              </button>
            </div>
            <button class="btn-gradient" style="width:100%;" @click="toggleAgentMenu">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/></svg>
              {{ isEnabled(drawerSkill) ? '启用到 Agent' : '安装并启用到 Agent' }}
            </button>
          </div>
        </div>
      </aside>
    </Teleport>

    <!-- ===================== 高级调试弹窗（两步：参数预检 → 真实执行） ===================== -->
    <Teleport to="body">
      <div v-if="debugSkill" class="modal-backdrop" @click.self="closeDebug">
        <div class="modal-card modal-card-lg">
          <div class="modal-header">
            <div class="modal-title">高级调试 - {{ debugSkill.name }}</div>
            <button class="modal-close" @click="closeDebug">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <div class="debug-steps">
              <span class="debug-step" :class="{ active: debugStep === 1, done: debugStep > 1 }">1</span>
              <span class="debug-step-label">参数预检</span>
              <span class="debug-step" :class="{ active: debugStep === 2 }">2</span>
              <span class="debug-step-label">真实执行</span>
            </div>

            <template v-if="debugStep === 1">
              <div class="form-group">
                <label class="form-label">调用参数（JSON）</label>
                <textarea v-model="debugParamsText" class="form-input form-textarea mono" rows="6" placeholder="{}"></textarea>
                <div class="form-hint">上下文（用户 / 工作空间）自动注入当前登录态，无需填写</div>
              </div>
              <div v-if="preview" class="run-result">
                <div class="run-result-head">
                  <span class="badge" :class="preview.valid ? 'badge-success' : 'badge-danger'">
                    {{ preview.valid ? '参数校验通过' : '参数校验未通过' }}
                  </span>
                </div>
                <ul v-if="!preview.valid" class="run-result-errors">
                  <li v-for="(err, idx) in preview.errors" :key="idx">{{ err }}</li>
                </ul>
                <div v-else class="run-result-plan">
                  <span class="run-result-plan-label">执行计划</span>
                  <span class="mono">{{ preview.plan }}</span>
                </div>
              </div>
            </template>

            <template v-else>
              <div v-if="preview" class="run-result" style="margin-bottom: 14px;">
                <div class="run-result-plan">
                  <span class="run-result-plan-label">执行计划</span>
                  <span class="mono">{{ preview.plan }}</span>
                </div>
              </div>
              <div class="form-group">
                <label class="form-label">关联会话 ID（可选）</label>
                <input v-model="debugSessionId" class="form-input mono" placeholder="留空则生成 debug- 前缀的调试会话" />
              </div>
              <div v-if="debugResult" class="run-result">
                <div class="run-result-head">
                  <span class="badge" :class="debugResult.success ? 'badge-success' : 'badge-danger'">
                    {{ debugResult.success ? '执行成功' : '执行失败' }}
                  </span>
                  <span v-if="debugResult.durationMs != null" class="run-latency">{{ debugResult.durationMs }}ms</span>
                </div>
                <pre v-if="debugResult.success" class="run-result-body">{{ formatJson(debugResult.data) }}</pre>
                <div v-else class="run-result-error">{{ debugResult.errorMessage }}</div>
                <div class="run-result-trace">
                  traceId: <span class="mono">{{ debugResult.traceId }}</span>
                  ｜ sessionId: <span class="mono">{{ debugResult.sessionId }}</span>
                </div>
              </div>
            </template>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary-custom" @click="closeDebug">关闭</button>
            <button v-if="debugStep === 2" class="btn-secondary-custom" @click="debugStep = 1">上一步</button>
            <button v-if="debugStep === 1" class="btn-gradient" :disabled="previewing" @click="handlePreview">
              <span v-if="previewing" class="btn-spinner"></span>
              {{ previewing ? '预检中...' : '参数预检' }}
            </button>
            <button v-else class="btn-gradient" :disabled="running" @click="handleRun">
              <span v-if="running" class="btn-spinner"></span>
              {{ running ? '执行中...' : '真实执行' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ===================== 编辑弹窗（用户 Skill：内容型 / API） ===================== -->
    <Teleport to="body">
      <div v-if="showFormModal" class="modal-backdrop" @click.self="closeFormModal">
        <div class="modal-card modal-card-lg">
          <div class="modal-header">
            <div class="modal-title">编辑 Skill</div>
            <button class="modal-close" @click="closeFormModal">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <div class="form-group">
              <label class="form-label">名称 <span class="required">*</span></label>
              <input v-model="form.name" class="form-input" placeholder="例如：周报生成助手 / 订单查询" maxlength="100" />
            </div>
            <div class="form-group">
              <label class="form-label">所属工种</label>
              <select v-model="form.category" class="form-input">
                <option v-for="c in SKILL_CATEGORIES" :key="c" :value="c">{{ c }}</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">描述 <span v-if="form.type === 'prompt'" class="required">*</span></label>
              <input v-model="form.description" class="form-input" placeholder="这个 Skill 做什么（Agent 凭描述判断何时加载/调用）" />
            </div>

            <template v-if="form.type === 'prompt'">
              <div class="form-group">
                <label class="form-label">指令内容（Markdown） <span class="required">*</span></label>
                <textarea
                  v-model="form.content"
                  class="form-input form-textarea"
                  rows="10"
                  placeholder="## 使用步骤&#10;1. 先给结论，再给依据&#10;2. ..."
                ></textarea>
                <div class="form-hint">渐进式披露：Agent 平时只看到名称和描述，判断与任务相关时自动加载全文</div>
              </div>
            </template>

            <template v-else>
              <div class="mode-switch">
                <button
                  type="button"
                  class="mode-link"
                  :class="{ active: !form.advanced }"
                  @click="form.advanced && switchToStructured()"
                >结构化表单</button>
                <button
                  type="button"
                  class="mode-link"
                  :class="{ active: form.advanced }"
                  @click="!form.advanced && switchToAdvanced()"
                >高级模式（JSON）</button>
              </div>

              <template v-if="!form.advanced">
                <div class="form-group">
                  <label class="form-label">接口地址（URL） <span class="required">*</span></label>
                  <input v-model="form.apiUrl" class="form-input mono" placeholder="https://api.example.com/query" />
                </div>
                <div class="form-row">
                  <div class="form-group form-group-half">
                    <label class="form-label">请求方法</label>
                    <select v-model="form.apiMethod" class="form-input">
                      <option v-for="m in ['GET', 'POST', 'PUT', 'DELETE']" :key="m" :value="m">{{ m }}</option>
                    </select>
                  </div>
                  <div class="form-group form-group-half">
                    <label class="form-label">超时时间（毫秒）</label>
                    <input v-model.number="form.apiTimeout" type="number" min="100" step="1000" class="form-input mono" />
                  </div>
                </div>
                <div class="form-group">
                  <label class="form-label">请求头（可选）</label>
                  <div v-for="(h, i) in form.apiHeaders" :key="'h' + i" class="kv-row">
                    <input v-model="h.key" class="form-input kv-input mono" placeholder="Key，如 Authorization" />
                    <input v-model="h.value" class="form-input kv-input mono" placeholder="Value" />
                    <button type="button" class="kv-remove" title="移除" @click="form.apiHeaders.splice(i, 1)">
                      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                        <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                      </svg>
                    </button>
                  </div>
                  <button type="button" class="kv-add" @click="form.apiHeaders.push({ key: '', value: '' })">+ 添加请求头</button>
                </div>
                <div class="form-group">
                  <label class="form-label">输入参数（可选，供 LLM 生成调用参数）</label>
                  <div v-for="(p, i) in form.apiParams" :key="'p' + i" class="kv-row param-row">
                    <input v-model="p.name" class="form-input mono" placeholder="参数名" />
                    <select v-model="p.type" class="form-input param-type">
                      <option value="string">string</option>
                      <option value="integer">integer</option>
                      <option value="number">number</option>
                      <option value="boolean">boolean</option>
                    </select>
                    <input v-model="p.description" class="form-input" placeholder="参数说明" />
                    <label class="param-required">
                      <input type="checkbox" v-model="p.required" />必填
                    </label>
                    <button type="button" class="kv-remove" title="移除" @click="form.apiParams.splice(i, 1)">
                      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                        <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                      </svg>
                    </button>
                  </div>
                  <button type="button" class="kv-add" @click="form.apiParams.push({ name: '', type: 'string', description: '', required: false })">+ 添加参数</button>
                </div>
              </template>

              <template v-else>
                <div class="form-group">
                  <label class="form-label">API 配置（JSON） <span class="required">*</span></label>
                  <textarea
                    v-model="form.config"
                    class="form-input form-textarea mono"
                    rows="5"
                    placeholder='{"url": "https://api.example.com/query", "method": "POST", "headers": {}, "timeout": 10000}'
                  ></textarea>
                  <div class="form-hint">必须包含 url；method 缺省 GET；支持 headers、timeout（毫秒）</div>
                </div>
                <div class="form-group">
                  <label class="form-label">输入参数 JSON Schema</label>
                  <textarea
                    v-model="form.inputSchema"
                    class="form-input form-textarea mono"
                    rows="6"
                    placeholder='{"type": "object", "properties": {"orderId": {"type": "string"}}, "required": ["orderId"]}'
                  ></textarea>
                  <div class="form-hint">供 LLM function calling 生成参数；留空表示无参数</div>
                </div>
              </template>
            </template>

            <div class="form-group">
              <label class="form-label">版本号</label>
              <input v-model="form.version" class="form-input" placeholder="1.0.0" />
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary-custom" @click="closeFormModal">取消</button>
            <button class="btn-gradient" :disabled="!canSubmit || submitting" @click="handleSubmit">
              <span v-if="submitting" class="btn-spinner"></span>
              {{ submitting ? '保存中...' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 删除确认 -->
    <Teleport to="body">
      <div v-if="deleteTarget" class="modal-backdrop" @click.self="deleteTarget = null">
        <div class="modal-card modal-card-sm">
          <div class="modal-header">
            <div class="modal-title confirm-title">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="color: var(--red);">
                <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
                <line x1="12" y1="9" x2="12" y2="13" />
                <line x1="12" y1="17" x2="12.01" y2="17" />
              </svg>
              删除 Skill
            </div>
            <button class="modal-close" @click="deleteTarget = null">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <p class="confirm-text">确定要删除 Skill「<strong>{{ deleteTarget.name }}</strong>」吗？</p>
            <p class="confirm-hint">若该 Skill 仍被 Agent 绑定，删除将被拒绝；请先在 Agent 详情中解绑。</p>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary-custom" @click="deleteTarget = null">取消</button>
            <button class="btn-danger" :disabled="deleting" @click="confirmDelete">
              <span v-if="deleting" class="btn-spinner"></span>
              {{ deleting ? '删除中...' : '确认删除' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useMessage, NPagination } from 'naive-ui'
import {
  SKILL_CATEGORIES,
  listSkills, listPlaza, updateSkill, deleteSkill,
  exportSkill, setSkillStatus, invokeSkill, listPackageFiles,
  debugPreview, debugRun, bindSkill,
  type SkillItem, type SkillForm, type SkillPackageFile,
  type InvokeSkillResult, type DebugPreviewResult, type DebugRunResult,
} from '@/services/skill'
import { listAgents } from '@/services/agent'
import type { Agent } from '@/types'

const message = useMessage()

/* ============================================================
   图标库（内联 SVG，Lucide 风格；静态内容，v-html 渲染）
   ============================================================ */
const ICONS: Record<string, string> = {
  write: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 013 3L7 19l-4 1 1-4z"/></svg>',
  headset: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 18v-6a9 9 0 0118 0v6"/><path d="M21 19a2 2 0 01-2 2h-1a2 2 0 01-2-2v-3a2 2 0 012-2h3zM3 19a2 2 0 002 2h1a2 2 0 002-2v-3a2 2 0 00-2-2H3z"/></svg>',
  users: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"/></svg>',
  calc: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="4" y="2" width="16" height="20" rx="2"/><path d="M8 6h8M8 12h.01M12 12h.01M16 12h.01M8 16h.01M12 16h.01M16 16h.01"/></svg>',
  scale: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 3v18M3 7h18"/><path d="M7 7l-3 7a3.5 3.5 0 006 0zM17 7l-3 7a3.5 3.5 0 006 0z"/></svg>',
  clip: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="8" y="2" width="8" height="4" rx="1"/><path d="M16 4h2a2 2 0 012 2v14a2 2 0 01-2 2H6a2 2 0 01-2-2V6a2 2 0 012-2h2"/><path d="M9 12h6M9 16h6"/></svg>',
  chart: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 3v18h18"/><path d="M18 17V9M13 17V5M8 17v-3"/></svg>',
  plug: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M9 2v6M15 2v6"/><path d="M6 8h12v4a6 6 0 01-6 6 6 6 0 01-6-6V8z"/><path d="M12 18v4"/></svg>',
  globe: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="10"/><path d="M2 12h20M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z"/></svg>',
  target: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="6"/><circle cx="12" cy="12" r="2"/></svg>',
  doc: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><path d="M14 2v6h6M16 13H8M16 17H8M10 9H8"/></svg>',
  spark: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/></svg>',
  folder: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z"/></svg>',
  file: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M13 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V9z"/><path d="M13 2v7h7"/></svg>',
  code: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M16 18l6-6-6-6M8 6l-6 6 6 6"/></svg>',
}

/** 技能图标底色（柔和渐变） */
const TILE: Record<string, string> = {
  indigo: 'linear-gradient(135deg,#EEF2FF,#E0E7FF)',
  purple: 'linear-gradient(135deg,#F5F3FF,#EDE9FE)',
  blue: 'linear-gradient(135deg,#EFF6FF,#DBEAFE)',
  cyan: 'linear-gradient(135deg,#ECFEFF,#CFFAFE)',
  green: 'linear-gradient(135deg,#ECFDF5,#D1FAE5)',
  orange: 'linear-gradient(135deg,#FFFBEB,#FEF3C7)',
  red: 'linear-gradient(135deg,#FEF2F2,#FEE2E2)',
}
const TILE_COLOR: Record<string, string> = {
  indigo: '#6366F1', purple: '#8B5CF6', blue: '#3B82F6', cyan: '#06B6D4',
  green: '#10B981', orange: '#F59E0B', red: '#EF4444',
}
const TILE_KEYS = Object.keys(TILE)

/** 工种导航词表（与后端 SKILL_CATEGORIES 一致，「全部」为前端聚合项） */
const CATS = ['全部', ...SKILL_CATEGORIES]
const CAT_ICONS: Record<string, string> = {
  '市场': 'target', '销售': 'chart', '客服': 'headset', '人事': 'users',
  '财务': 'calc', '法务合规': 'scale', '行政': 'clip', '数据分析': 'chart',
  'IT集成': 'plug', '其他': 'folder',
}

function tileOf(id: string): string {
  let h = 0
  for (const ch of id) h = (h * 31 + ch.charCodeAt(0)) >>> 0
  return TILE_KEYS[h % TILE_KEYS.length]
}

function iconOf(s: SkillItem): string {
  if (s.type === 'api') return 'plug'
  if (s.type === 'builtin') return 'spark'
  if (s.type === 'mcp') return 'globe'
  return 'write'
}

function iconStyle(s: SkillItem) {
  const t = tileOf(s.id)
  return { background: TILE[t], color: TILE_COLOR[t] }
}

function sourceBadgeOf(s: SkillItem): { cls: string; text: string } {
  if (s.type === 'builtin' || s.source === 'agentone') return { cls: 'badge-builtin', text: '内置' }
  if (s.type === 'mcp') return { cls: 'badge-mcp', text: 'MCP' }
  if (s.source === 'imported') return { cls: 'badge-imported', text: '导入' }
  if (s.source === 'official') return { cls: 'badge-official', text: '官方' }
  return { cls: 'badge-custom', text: '自建' }
}

function authorOf(s: SkillItem): string {
  if (s.type === 'builtin' || s.source === 'agentone') return '系统内置'
  if (s.type === 'mcp') return `MCP · ${s.source}`
  if (s.source === 'imported') return '导入'
  if (s.source === 'official') return 'AgentOne 官方'
  return '自建'
}

function typeLabel(type: string): string {
  const map: Record<string, string> = {
    builtin: '系统内置', api: 'API 封装', prompt: '指令模板', mcp: 'MCP 工具', market: '市场安装',
  }
  return map[type] || type
}

function typeBadge(type: string): string {
  const map: Record<string, string> = {
    builtin: 'badge-neutral', api: 'badge-indigo', prompt: 'badge-success', mcp: 'badge-purple',
  }
  return map[type] || 'badge-neutral'
}

/** 用户创建型 Skill（api/prompt）：可编辑/导出/删除；builtin/mcp 为系统托管 */
function isUserType(type: string): boolean {
  return type === 'api' || type === 'prompt'
}

function isSystemManaged(s: SkillItem): boolean {
  return s.type === 'builtin'
}

/** 行状态：用户 Skill 看 status；MCP 工具看是否已发布到广场 */
function isEnabled(s: SkillItem): boolean {
  if (s.type === 'mcp') return !!s.published
  return s.status === 'active'
}

function toggleTitle(s: SkillItem): string {
  if (s.type === 'mcp') return isEnabled(s) ? '点击从广场下架' : '点击发布到广场'
  return isEnabled(s) ? '点击停用' : '点击启用'
}

function formatDate(v: string | null | undefined): string {
  if (!v) return '—'
  const d = new Date(v)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleDateString()
}

/* ============================================================
   视图切换
   ============================================================ */
const route = useRoute()
// 支持 ?tab=mine / ?tab=plaza 直达对应标签页（发布成功页跳转用）
const view = ref<'plaza' | 'mine'>(route.query.tab === 'mine' ? 'mine' : 'plaza')

function switchView(v: 'plaza' | 'mine') {
  view.value = v
  // 切回「我的技能」时始终重新拉取，保证编辑/删除后列表即时刷新
  if (v === 'mine') loadSkills()
}

/* ============================================================
   广场
   ============================================================ */
const plazaSkills = ref<SkillItem[]>([])
const plazaLoading = ref(false)
const plazaSearchInput = ref('')
const plazaKeyword = ref('')
const activeCat = ref('全部')

const officialFeatured = computed(() => plazaSkills.value.filter((s) => s.source === 'official'))

async function loadPlaza() {
  plazaLoading.value = true
  try {
    const res = await listPlaza({
      q: plazaKeyword.value || undefined,
      cat: activeCat.value === '全部' ? undefined : activeCat.value,
    })
    plazaSkills.value = res.data.data || []
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    plazaLoading.value = false
  }
}

function setCat(c: string) {
  activeCat.value = c
  loadPlaza()
}

function clearPlazaFilters() {
  plazaSearchInput.value = ''
  plazaKeyword.value = ''
  activeCat.value = '全部'
  loadPlaza()
}

// 搜索防抖：输入停顿 300ms 后触发查询
let plazaTimer: ReturnType<typeof setTimeout> | undefined
watch(plazaSearchInput, (v) => {
  clearTimeout(plazaTimer)
  plazaTimer = setTimeout(() => {
    const next = v.trim()
    if (next !== plazaKeyword.value) {
      plazaKeyword.value = next
      loadPlaza()
    }
  }, 300)
})

/* ============================================================
   我的技能
   ============================================================ */
const skills = ref<SkillItem[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const mineTotal = ref(0)
const mineKeywordInput = ref('')
const mineKeyword = ref('')

async function loadSkills() {
  loading.value = true
  try {
    const res = await listSkills({
      page: page.value,
      size: pageSize.value,
      keyword: mineKeyword.value || undefined,
    })
    skills.value = res.data.data?.records || []
    mineTotal.value = res.data.data?.total || 0
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

let mineTimer: ReturnType<typeof setTimeout> | undefined
watch(mineKeywordInput, (v) => {
  clearTimeout(mineTimer)
  mineTimer = setTimeout(() => {
    const next = v.trim()
    if (next !== mineKeyword.value) {
      mineKeyword.value = next
      page.value = 1
      loadSkills()
    }
  }, 300)
})

function handlePageChange(p: number) {
  page.value = p
  loadSkills()
}

/** 状态 toggle：用户 Skill 启停；MCP 工具发布/下架（builtin 前端已隐藏 toggle） */
async function handleToggle(s: SkillItem, on: boolean) {
  try {
    await setSkillStatus(s.id, on)
    if (s.type === 'mcp') {
      message.success(on ? `已将「${s.name}」发布到广场` : `已将「${s.name}」从广场下架`)
    } else {
      message.success(on ? `已启用「${s.name}」` : `已停用「${s.name}」`)
    }
    await Promise.all([loadSkills(), loadPlaza()])
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '操作失败')
  }
}

/* ============================================================
   详情抽屉
   ============================================================ */
const drawerSkill = ref<SkillItem | null>(null)
const drawerOpen = ref(false)
const packageFiles = ref<SkillPackageFile[]>([])
const tryOpen = ref(false)
const tryState = ref<'idle' | 'confirm' | 'result'>('idle')
const tryValues = ref<Record<string, string>>({})
const tryResult = ref<InvokeSkillResult | null>(null)
const confirmToken = ref('')
const draftParams = ref<Record<string, unknown> | null>(null)
const invoking = ref(false)
const agents = ref<Agent[]>([])
const agentMenuOpen = ref(false)

interface TryField { key: string; type: string; required: boolean; description: string; options?: string[] }

/** 按 inputSchema 展开试一试表单字段 */
function tryFieldsOf(s: SkillItem): TryField[] {
  try {
    const schema = JSON.parse(s.inputSchema) as {
      properties?: Record<string, { type?: string; description?: string; enum?: string[] }>
      required?: string[]
    }
    const props = schema?.properties
    if (!props || typeof props !== 'object') return []
    const requiredList = Array.isArray(schema.required) ? schema.required : []
    return Object.entries(props).map(([key, p]) => ({
      key,
      type: p?.type || 'string',
      required: requiredList.includes(key),
      description: p?.description || '',
      options: Array.isArray(p?.enum) && p.enum.length > 0 ? p.enum : undefined,
    }))
  } catch {
    return []
  }
}
const tryFields = computed<TryField[]>(() => (drawerSkill.value ? tryFieldsOf(drawerSkill.value) : []))

/** 内容型技能的指令内容（config.content），坏数据回显原文 */
const promptContent = computed(() => {
  const s = drawerSkill.value
  if (!s || s.type !== 'prompt') return ''
  try {
    const parsed = JSON.parse(s.config) as { content?: unknown }
    return typeof parsed?.content === 'string' ? parsed.content : s.config
  } catch {
    return s.config
  }
})

async function openDrawer(s: SkillItem, autoTry = false) {
  drawerSkill.value = s
  tryOpen.value = autoTry
  tryState.value = 'idle'
  tryResult.value = null
  confirmToken.value = ''
  draftParams.value = null
  tryValues.value = {}
  agentMenuOpen.value = false
  packageFiles.value = []
  drawerOpen.value = true
  // 技能包文件树：仅导入的技能包可能有，失败（5002 等）静默处理
  try {
    const res = await listPackageFiles(s.id)
    packageFiles.value = res.data.data || []
  } catch {
    packageFiles.value = []
  }
}

function closeDrawer() {
  drawerOpen.value = false
}

function fileIconOf(kind: string): string {
  if (kind === 'script') return 'code'
  if (kind === 'doc') return 'doc'
  return 'file'
}

function fileKindLabel(kind: string): string {
  if (kind === 'script') return '脚本 · 不执行'
  if (kind === 'doc') return '说明'
  return '资源'
}

/** 表单值 → 调用参数（按 schema 类型转换） */
function buildTryParams(): Record<string, unknown> | null {
  const params: Record<string, unknown> = {}
  for (const f of tryFields.value) {
    const raw = (tryValues.value[f.key] ?? '').trim()
    if (f.required && raw === '') {
      message.error(`请填写「${f.key}」`)
      return null
    }
    if (raw === '') continue
    if (f.type === 'integer' || f.type === 'number') {
      const n = Number(raw)
      if (Number.isNaN(n)) {
        message.error(`「${f.key}」需要是数字`)
        return null
      }
      params[f.key] = n
    } else if (f.type === 'boolean') {
      params[f.key] = raw === 'true'
    } else if (f.type === 'object' || f.type === 'array') {
      try {
        params[f.key] = JSON.parse(raw)
      } catch {
        message.error(`「${f.key}」需要是合法 JSON`)
        return null
      }
    } else {
      params[f.key] = raw
    }
  }
  return params
}

/** 后端 skill_call_log 统计口径：每次真实调用（含失败）计一次，草稿阶段不计；
 * 本地同步 +1，让抽屉与卡片展示即时刷新 */
function bumpCallCount() {
  if (drawerSkill.value) {
    drawerSkill.value.callCount = (drawerSkill.value.callCount ?? 0) + 1
  }
}

/** 试一试：动作型技能首次返回草稿 + token，确认后才真实执行 */
async function runTry() {
  if (!drawerSkill.value) return
  const params = buildTryParams()
  if (params === null) return
  invoking.value = true
  try {
    const res = await invokeSkill(drawerSkill.value.id, params)
    const v = res.data.data
    if (v?.confirmRequired) {
      tryState.value = 'confirm'
      confirmToken.value = v.confirmToken || ''
      draftParams.value = v.draftParams
    } else {
      tryState.value = 'result'
      tryResult.value = v
      bumpCallCount()
    }
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '运行失败')
  } finally {
    invoking.value = false
  }
}

async function confirmTry() {
  if (!drawerSkill.value) return
  const params = buildTryParams()
  if (params === null) return
  invoking.value = true
  try {
    const res = await invokeSkill(drawerSkill.value.id, params, confirmToken.value)
    tryState.value = 'result'
    tryResult.value = res.data.data
    bumpCallCount()
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '执行失败')
  } finally {
    invoking.value = false
  }
}

/** 启用到 Agent：懒加载 Agent 列表，选中后绑定 */
async function toggleAgentMenu() {
  agentMenuOpen.value = !agentMenuOpen.value
  if (agentMenuOpen.value && agents.value.length === 0) {
    try {
      const res = await listAgents(1, 100)
      agents.value = res.data.data?.records || []
    } catch {
      agents.value = []
    }
  }
}

async function enableToAgent(agentId: string, agentName: string) {
  if (!drawerSkill.value) return
  agentMenuOpen.value = false
  try {
    await bindSkill({ agentId, skillId: drawerSkill.value.id })
    message.success(`已启用到「${agentName}」，去对话页即可使用`)
    await Promise.all([loadSkills(), loadPlaza()])
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '绑定失败')
  }
}

/* ============================================================
   高级调试（参数预检 → 真实执行，上下文取当前登录态）
   ============================================================ */
const debugSkill = ref<SkillItem | null>(null)
const debugStep = ref(1)
const debugParamsText = ref('{}')
const debugSessionId = ref('')
const preview = ref<DebugPreviewResult | null>(null)
const previewing = ref(false)
const debugResult = ref<DebugRunResult | null>(null)
const running = ref(false)

function openDebug(s: SkillItem) {
  debugSkill.value = s
  debugStep.value = 1
  debugParamsText.value = sampleParamsOf(s.inputSchema)
  debugSessionId.value = ''
  preview.value = null
  debugResult.value = null
}

function closeDebug() {
  debugSkill.value = null
}

async function handlePreview() {
  if (!debugSkill.value) return
  const params = parseParams(debugParamsText.value)
  if (params === null) return
  previewing.value = true
  try {
    const res = await debugPreview(debugSkill.value.id, params)
    preview.value = res.data.data
    if (preview.value?.valid) {
      debugStep.value = 2
    }
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '预检失败')
  } finally {
    previewing.value = false
  }
}

async function handleRun() {
  if (!debugSkill.value) return
  const params = parseParams(debugParamsText.value)
  if (params === null) return
  running.value = true
  try {
    const res = await debugRun(debugSkill.value.id, params, debugSessionId.value.trim() || undefined)
    debugResult.value = res.data.data
    message.success(debugResult.value?.success ? '执行成功（已写入调试审计）' : '执行完成，结果为失败')
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '执行失败')
  } finally {
    running.value = false
  }
}

/* ============================================================
   编辑用户 Skill
   ============================================================ */
interface HeaderRow { key: string; value: string }
interface ParamRow { name: string; type: string; description: string; required: boolean }

const showFormModal = ref(false)
const editingSkill = ref<SkillItem | null>(null)
const submitting = ref(false)
const form = ref(emptyForm())

function emptyForm() {
  return {
    type: 'prompt' as 'prompt' | 'api',
    name: '',
    description: '',
    category: '其他',
    content: '',
    version: '1.0.0',
    apiUrl: '',
    apiMethod: 'GET',
    apiTimeout: 10000,
    apiHeaders: [] as HeaderRow[],
    apiParams: [] as ParamRow[],
    advanced: false,
    config: '',
    inputSchema: '',
  }
}

const canSubmit = computed(() => {
  if (!form.value.name.trim()) return false
  if (form.value.type === 'prompt') {
    // 描述是渐进式披露的触发依据，内容型 Skill 必填
    return form.value.content.trim() !== '' && form.value.description.trim() !== ''
  }
  if (form.value.advanced) return form.value.config.trim() !== ''
  return form.value.apiUrl.trim() !== ''
})

function openEditModal(s: SkillItem) {
  editingSkill.value = s
  form.value = emptyForm()
  form.value.type = s.type === 'prompt' ? 'prompt' : 'api'
  form.value.name = s.name
  form.value.description = s.description || ''
  form.value.category = s.category || '其他'
  form.value.version = s.version || ''
  if (s.type === 'prompt') {
    form.value.content = promptContentOf(s.config)
  } else if (!parseApiIntoForm(s.config, s.inputSchema)) {
    // 无法解析为结构化字段（旧数据 / 手写 JSON）→ 直接进高级模式回显原文
    form.value.advanced = true
    form.value.config = prettyOrRaw(s.config)
    form.value.inputSchema = prettyOrRaw(s.inputSchema)
  }
  showFormModal.value = true
}

/** 从 prompt Skill 的 config JSON 中取出指令内容；坏数据回显原文便于修复 */
function promptContentOf(config: string): string {
  try {
    const parsed = JSON.parse(config) as { content?: unknown }
    return typeof parsed?.content === 'string' ? parsed.content : config
  } catch {
    return config
  }
}

function closeFormModal() {
  showFormModal.value = false
  editingSkill.value = null
}

/** 结构化字段 → config JSON */
function buildApiConfig(): Record<string, unknown> {
  const headers: Record<string, string> = {}
  for (const h of form.value.apiHeaders) {
    if (h.key.trim()) headers[h.key.trim()] = h.value
  }
  const config: Record<string, unknown> = {
    url: form.value.apiUrl.trim(),
    method: form.value.apiMethod,
    timeout: Number(form.value.apiTimeout) > 0 ? Number(form.value.apiTimeout) : 10000,
  }
  if (Object.keys(headers).length > 0) config.headers = headers
  return config
}

/** 参数表 → JSON Schema 字符串；无参数返回 undefined */
function buildInputSchema(): string | undefined {
  const properties: Record<string, Record<string, unknown>> = {}
  const required: string[] = []
  for (const p of form.value.apiParams) {
    const name = p.name.trim()
    if (!name) continue
    const prop: Record<string, unknown> = { type: p.type || 'string' }
    if (p.description.trim()) prop.description = p.description.trim()
    properties[name] = prop
    if (p.required) required.push(name)
  }
  if (Object.keys(properties).length === 0) return undefined
  const schema: Record<string, unknown> = { type: 'object', properties }
  if (required.length > 0) schema.required = required
  return JSON.stringify(schema)
}

/** config/inputSchema JSON → 结构化字段；失败返回 false（调用方降级高级模式） */
function parseApiIntoForm(configJson: string, schemaJson: string): boolean {
  try {
    const cfg = JSON.parse(configJson) as Record<string, unknown>
    if (typeof cfg.url !== 'string' || !cfg.url) return false
    form.value.apiUrl = cfg.url
    form.value.apiMethod = typeof cfg.method === 'string' ? cfg.method.toUpperCase() : 'GET'
    form.value.apiTimeout = typeof cfg.timeout === 'number' ? cfg.timeout : 10000
    form.value.apiHeaders = cfg.headers && typeof cfg.headers === 'object'
      ? Object.entries(cfg.headers as Record<string, unknown>).map(([key, value]) => ({ key, value: String(value) }))
      : []
    form.value.apiParams = []
    if (schemaJson && schemaJson.trim() && schemaJson.trim() !== '{}') {
      const schema = JSON.parse(schemaJson) as {
        properties?: Record<string, { type?: string; description?: string }>
        required?: string[]
      }
      const requiredList = Array.isArray(schema.required) ? schema.required : []
      form.value.apiParams = Object.entries(schema.properties || {}).map(([name, prop]) => ({
        name,
        type: prop?.type || 'string',
        description: prop?.description || '',
        required: requiredList.includes(name),
      }))
    }
    return true
  } catch {
    return false
  }
}

function switchToAdvanced() {
  form.value.config = JSON.stringify(buildApiConfig(), null, 2)
  const schema = buildInputSchema()
  form.value.inputSchema = schema ? JSON.stringify(JSON.parse(schema), null, 2) : ''
  form.value.advanced = true
}

function switchToStructured() {
  if (!parseApiIntoForm(form.value.config || '{}', form.value.inputSchema)) {
    message.error('当前 JSON 无法解析为结构化表单（需包含 url），请修正后再切换')
    return
  }
  form.value.advanced = false
}

async function handleSubmit() {
  if (!canSubmit.value || !editingSkill.value) return

  let config: string
  let inputSchema: string | undefined
  if (form.value.type === 'prompt') {
    config = JSON.stringify({ content: form.value.content })
  } else if (form.value.advanced) {
    // 高级模式：前端先做 JSON 预检，错误提示比后端 400 更友好
    try {
      JSON.parse(form.value.config)
    } catch {
      message.error('API 配置不是合法 JSON')
      return
    }
    if (form.value.inputSchema.trim()) {
      try {
        JSON.parse(form.value.inputSchema)
      } catch {
        message.error('输入参数 Schema 不是合法 JSON')
        return
      }
    }
    config = form.value.config.trim()
    inputSchema = form.value.inputSchema.trim() || undefined
  } else {
    if (!/^https?:\/\//i.test(form.value.apiUrl.trim())) {
      message.error('接口地址必须以 http:// 或 https:// 开头')
      return
    }
    config = JSON.stringify(buildApiConfig())
    inputSchema = buildInputSchema()
  }

  submitting.value = true
  try {
    const payload: SkillForm = {
      name: form.value.name.trim(),
      type: form.value.type,
      category: form.value.category,
      description: form.value.description.trim() || undefined,
      config,
      inputSchema: form.value.type === 'api' ? inputSchema : undefined,
      version: form.value.version.trim() || undefined,
    }
    await updateSkill(editingSkill.value.id, payload)
    message.success('更新成功')
    closeFormModal()
    await Promise.all([loadSkills(), loadPlaza()])
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    submitting.value = false
  }
}

/* ============================================================
   导出（JSON 定义） / 删除
   导入入口已统一到创建向导「导入技能包 → 导入导出文件 .json」
   ============================================================ */
async function handleExport(s: SkillItem) {
  try {
    const res = await exportSkill(s.id)
    const payload = res.data.data
    if (!payload) return
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `skill-${s.name}.json`
    link.click()
    URL.revokeObjectURL(url)
    message.success(`Skill「${s.name}」已导出`)
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '导出失败')
  }
}

const deleteTarget = ref<SkillItem | null>(null)
const deleting = ref(false)

async function confirmDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await deleteSkill(deleteTarget.value.id)
    message.success(`Skill「${deleteTarget.value.name}」已删除`)
    deleteTarget.value = null
    await Promise.all([loadSkills(), loadPlaza()])
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '删除失败')
  } finally {
    deleting.value = false
  }
}

/* ============================================================
   helpers
   ============================================================ */
function parseParams(text: string): Record<string, unknown> | null {
  const raw = text.trim() || '{}'
  try {
    const parsed: unknown = JSON.parse(raw)
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      message.error('参数必须是 JSON 对象')
      return null
    }
    return parsed as Record<string, unknown>
  } catch {
    message.error('参数不是合法 JSON')
    return null
  }
}

/** 按 inputSchema 生成示例参数骨架，降低手填成本 */
function sampleParamsOf(inputSchema: string): string {
  try {
    const schema = JSON.parse(inputSchema) as { properties?: Record<string, { type?: string }> }
    const props = schema?.properties
    if (!props || typeof props !== 'object') return '{}'
    const sample: Record<string, unknown> = {}
    for (const [key, prop] of Object.entries(props)) {
      switch (prop?.type) {
        case 'integer':
        case 'number':
          sample[key] = 0
          break
        case 'boolean':
          sample[key] = true
          break
        case 'object':
          sample[key] = {}
          break
        case 'array':
          sample[key] = []
          break
        default:
          sample[key] = ''
      }
    }
    return JSON.stringify(sample, null, 2)
  } catch {
    return '{}'
  }
}

function prettyOrRaw(json: string | null | undefined): string {
  if (!json) return ''
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
  }
}

function formatJson(data: unknown): string {
  if (data === null || data === undefined) return '(空)'
  if (typeof data === 'string') return prettyOrRaw(data) || '(空)'
  return JSON.stringify(data, null, 2)
}

/* ============================================================
   生命周期
   ============================================================ */
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    if (agentMenuOpen.value) {
      agentMenuOpen.value = false
      return
    }
    if (drawerOpen.value) closeDrawer()
  }
}

onMounted(() => {
  loadPlaza()
  loadSkills()
  document.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
  // 清理未触发的搜索防抖定时器，避免离开页面后仍发出请求
  if (plazaTimer) clearTimeout(plazaTimer)
  if (mineTimer) clearTimeout(mineTimer)
})
</script>

<style scoped>
.page-container {
  animation: pageIn 0.4s ease;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 22px;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.toolbar-actions .btn-gradient {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  text-decoration: none;
}

/* Tabs（广场 / 我的技能） */
.tabs {
  display: flex;
  gap: 4px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 4px;
  width: fit-content;
  margin-bottom: 20px;
}

.tab {
  padding: 8px 20px;
  border-radius: var(--radius-sm);
  border: none;
  background: transparent;
  font-size: 13.5px;
  font-weight: 700;
  color: var(--text-muted);
  transition: var(--transition);
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.tab:hover {
  color: var(--text);
}

.tab.active {
  background: var(--grad-primary-2);
  color: #fff;
  box-shadow: var(--glow-primary);
}

.tab .count {
  font-size: 11px;
  padding: 1px 7px;
  border-radius: var(--radius-full);
  background: var(--surface-alt);
  color: var(--text-muted);
}

.tab.active .count {
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
}

/* 广场工具栏 */
.plaza-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.search-box {
  position: relative;
  flex: 1;
  min-width: 240px;
  max-width: 480px;
}

.search-icon {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-placeholder);
  pointer-events: none;
}

.search-input {
  width: 100%;
  padding: 11px 38px 11px 40px;
  border-radius: var(--radius);
  border: 1.5px solid var(--border);
  font-size: 13.5px;
  font-family: inherit;
  background: var(--surface);
  transition: var(--transition);
  outline: none;
  box-sizing: border-box;
  color: var(--text);
}

.search-input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}

.search-input::placeholder {
  color: var(--text-placeholder);
}

.search-clear {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: none;
  background: var(--surface-alt);
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: var(--transition);
}

.search-clear:hover {
  background: var(--border);
  color: var(--text);
}

/* 工种 chips */
.chips {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 4px;
  margin-bottom: 20px;
}

.chips::-webkit-scrollbar {
  height: 0;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 15px;
  border-radius: var(--radius-full);
  border: 1.5px solid var(--border);
  background: var(--surface);
  font-size: 12.5px;
  font-weight: 700;
  color: var(--text-secondary);
  transition: var(--transition);
  white-space: nowrap;
  cursor: pointer;
}

.chip-icon {
  display: inline-flex;
  width: 15px;
  height: 15px;
}

.chip:hover {
  border-color: var(--primary-border);
  color: var(--primary);
}

.chip.active {
  background: var(--grad-primary-2);
  color: #fff;
  border-color: transparent;
  box-shadow: var(--glow-primary);
}

/* section 标题 */
.section-title {
  font-size: 16px;
  font-weight: 700;
  margin: 8px 0 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  letter-spacing: -0.2px;
  color: var(--text);
}

.section-title::before {
  content: '';
  width: 4px;
  height: 18px;
  border-radius: 2px;
  background: var(--grad-primary);
}

.section-title .hint {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 500;
  margin-left: auto;
}

/* 技能卡片 */
.skill-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  margin-bottom: 30px;
}

.skill-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 18px;
  cursor: pointer;
  transition: var(--transition);
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.skill-card::after {
  content: '';
  position: absolute;
  top: -40%;
  right: -30%;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: var(--grad-primary);
  opacity: 0.05;
  filter: blur(36px);
  transition: var(--transition);
  pointer-events: none;
}

.skill-card:hover {
  box-shadow: var(--shadow-lg), var(--glow-primary);
  transform: translateY(-3px);
  border-color: var(--primary-border);
}

.skill-card:hover::after {
  opacity: 0.12;
}

.skill-card-top {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.skill-icon {
  width: 46px;
  height: 46px;
  border-radius: var(--radius);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.skill-icon :deep(svg) {
  width: 22px;
  height: 22px;
}

.skill-card-title {
  flex: 1;
  min-width: 0;
}

.skill-name {
  font-size: 15px;
  font-weight: 800;
  letter-spacing: -0.2px;
  line-height: 1.3;
  color: var(--text);
}

.skill-author {
  font-size: 11.5px;
  color: var(--text-muted);
  margin-top: 2px;
  font-weight: 500;
}

.skill-tagline {
  font-size: 12.5px;
  color: var(--text-secondary);
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 38px;
}

.skill-card-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.spacer {
  flex: 1;
}

.installs {
  font-size: 11px;
  color: var(--text-muted);
  font-weight: 600;
}

/* 徽章 */
.badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 9px;
  border-radius: 20px;
  font-size: 10.5px;
  font-weight: 700;
  border: 1px solid transparent;
  white-space: nowrap;
}

.badge-official { background: var(--indigo-bg); color: var(--indigo); border-color: var(--indigo-border); }
.badge-custom { background: var(--surface-alt); color: var(--text-secondary); border-color: var(--border); }
.badge-imported { background: var(--cyan-bg); color: var(--cyan); border-color: var(--cyan-border); }
.badge-builtin { background: var(--purple-bg); color: var(--purple); border-color: var(--purple-border); }
.badge-mcp { background: var(--blue-bg); color: var(--blue); border-color: var(--blue-border); }
.badge-cat { background: var(--surface-alt); color: var(--text-muted); border-color: var(--border); }
.badge-script { background: var(--orange-bg); color: var(--orange); border-color: var(--orange-border); }
.badge-on { background: var(--green-bg); color: var(--green); border-color: var(--green-border); }
.badge-neutral { background: var(--surface-alt); color: var(--text-secondary); border-color: var(--border); }
.badge-indigo { background: var(--indigo-bg); color: var(--indigo); border-color: var(--indigo-border); }
.badge-success { background: var(--green-bg); color: var(--green); border-color: var(--green-border); }
.badge-purple { background: var(--purple-bg); color: var(--purple); border-color: var(--purple-border); }
.badge-danger { background: var(--red-bg); color: var(--red); border-color: var(--red-border); }

/* 我的技能列表 */
.list-wrap {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.list-head,
.skill-row {
  display: grid;
  grid-template-columns: minmax(220px, 2fr) 110px 110px 110px 110px 170px;
  gap: 14px;
  align-items: center;
  padding: 14px 20px;
}

.list-head {
  background: var(--surface-alt);
  border-bottom: 1px solid var(--border);
  font-size: 11.5px;
  font-weight: 700;
  color: var(--text-muted);
  letter-spacing: 0.3px;
}

.skill-row {
  border-bottom: 1px solid var(--border-light);
  transition: var(--transition);
}

.skill-row:last-child {
  border-bottom: none;
}

.skill-row:hover {
  background: var(--surface-alt);
}

.row-skill {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  cursor: pointer;
}

.row-skill .skill-icon {
  width: 38px;
  height: 38px;
  border-radius: var(--radius-sm);
}

.row-skill .skill-icon :deep(svg) {
  width: 18px;
  height: 18px;
}

.row-name {
  font-weight: 700;
  font-size: 13.5px;
  line-height: 1.3;
  color: var(--text);
}

.row-sub {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 3px;
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
}

.row-stat {
  font-size: 12.5px;
  color: var(--text-secondary);
  font-weight: 600;
}

.row-stat small {
  display: block;
  font-size: 10.5px;
  color: var(--text-muted);
  font-weight: 500;
}

.row-actions {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
  align-items: center;
}

.icon-btn {
  width: 30px;
  height: 30px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text-muted);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: var(--transition);
  cursor: pointer;
}

.icon-btn svg {
  width: 15px;
  height: 15px;
}

.icon-btn:hover {
  border-color: var(--primary-border);
  color: var(--primary);
}

.icon-btn.danger:hover {
  border-color: var(--red-border);
  color: var(--red);
}

.managed-tag {
  font-size: 11px;
  color: var(--text-placeholder);
  background: var(--surface-alt);
  border: 1px solid var(--border);
  padding: 3px 9px;
  border-radius: var(--radius-full);
  font-weight: 600;
}

.managed-hint {
  font-size: 11px;
  color: var(--text-placeholder);
}

.mono {
  font-family: 'JetBrains Mono', monospace;
}

/* toggle */
.toggle {
  position: relative;
  display: inline-block;
  width: 40px;
  height: 22px;
  flex-shrink: 0;
}

.toggle input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle .track {
  position: absolute;
  inset: 0;
  background: var(--border-strong);
  border-radius: 9999px;
  transition: var(--transition);
  cursor: pointer;
}

.toggle .track::after {
  content: '';
  position: absolute;
  top: 3px;
  left: 3px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  transition: var(--transition);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

.toggle input:checked + .track {
  background: var(--grad-primary-2);
}

.toggle input:checked + .track::after {
  transform: translateX(18px);
}

/* 空态 */
.empty {
  padding: 70px 20px;
  text-align: center;
  border: 1.5px dashed var(--border-strong);
  border-radius: var(--radius-lg);
  background: var(--surface);
}

.empty-icon {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-lg);
  margin: 0 auto 16px;
  background: var(--primary-light);
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-icon svg {
  width: 30px;
  height: 30px;
  color: var(--primary);
}

.empty-title {
  font-weight: 800;
  font-size: 16px;
  margin-bottom: 6px;
  color: var(--text);
}

.empty-desc {
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 20px;
}

.empty-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
  flex-wrap: wrap;
}

.empty-actions .btn-gradient {
  display: inline-flex;
  align-items: center;
  text-decoration: none;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* ============================================================
   详情抽屉
   ============================================================ */
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  backdrop-filter: blur(2px);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.3s;
  z-index: 1900;
}

.overlay.show {
  opacity: 1;
  pointer-events: auto;
}

.drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: 480px;
  max-width: 100vw;
  background: var(--surface);
  box-shadow: -16px 0 48px rgba(15, 23, 42, 0.16);
  z-index: 2000;
  transform: translateX(105%);
  transition: transform 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
}

.drawer.show {
  transform: translateX(0);
}

.drawer-head {
  padding: 24px 24px 18px;
  border-bottom: 1px solid var(--border-light);
  position: relative;
}

.drawer-close {
  position: absolute;
  top: 18px;
  right: 18px;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: var(--transition);
  cursor: pointer;
}

.drawer-close:hover {
  color: var(--text);
  border-color: var(--border-strong);
}

.drawer-close svg {
  width: 16px;
  height: 16px;
}

.drawer-title-row {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding-right: 40px;
}

.drawer-icon {
  width: 54px;
  height: 54px;
  border-radius: var(--radius);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.drawer-icon :deep(svg) {
  width: 26px;
  height: 26px;
}

.drawer-name {
  font-size: 19px;
  font-weight: 800;
  letter-spacing: -0.3px;
  line-height: 1.25;
  color: var(--text);
}

.drawer-badges {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 7px;
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 22px 24px;
}

.drawer-section {
  margin-bottom: 24px;
}

.drawer-label {
  font-size: 11px;
  font-weight: 800;
  color: var(--text-muted);
  letter-spacing: 0.6px;
  text-transform: uppercase;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.drawer-label::before {
  content: '';
  width: 3px;
  height: 12px;
  border-radius: 2px;
  background: var(--grad-primary);
}

.drawer-desc {
  font-size: 13.5px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.prompt-content {
  margin: 0;
  background: var(--surface-alt);
  border: 1px solid var(--border-light);
  border-radius: var(--radius);
  padding: 12px 14px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 220px;
  overflow-y: auto;
  font-family: inherit;
}

/* 技能包文件树 */
.file-tree {
  background: var(--surface-alt);
  border: 1px solid var(--border-light);
  border-radius: var(--radius);
  padding: 12px 14px;
}

.file-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 6px;
  border-radius: var(--radius-xs);
  font-size: 12.5px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  color: var(--text);
}

.file-node:hover {
  background: var(--surface);
}

.file-node-icon {
  display: inline-flex;
  width: 14px;
  height: 14px;
  color: var(--text-muted);
  flex-shrink: 0;
}

.file-node .f-type {
  margin-left: auto;
  font-size: 10px;
  color: var(--text-placeholder);
  font-family: inherit;
}

.file-tree-note {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 8px;
}

/* 试一试面板 */
.try-panel {
  background: var(--surface-alt);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
}

.try-head {
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  font-weight: 700;
  font-size: 13px;
  user-select: none;
  color: var(--text);
}

.try-head svg {
  width: 16px;
  height: 16px;
  color: var(--primary);
}

.try-head .chev {
  margin-left: auto;
  transition: transform 0.25s;
  width: 14px;
  height: 14px;
  color: var(--text-muted);
}

.try-panel.open .try-head .chev {
  transform: rotate(180deg);
}

.try-body {
  padding: 0 16px 16px;
  display: none;
}

.try-panel.open .try-body {
  display: block;
}

.field {
  margin-bottom: 12px;
}

.field label {
  display: block;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.field label .req {
  color: var(--red);
}

.field input,
.field select,
.field textarea {
  width: 100%;
  padding: 9px 12px;
  border-radius: var(--radius-sm);
  border: 1.5px solid var(--border);
  font-size: 13px;
  font-family: inherit;
  outline: none;
  transition: var(--transition);
  background: var(--surface);
  color: var(--text);
  box-sizing: border-box;
}

.field input:focus,
.field select:focus,
.field textarea:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
}

.field textarea {
  resize: vertical;
  min-height: 60px;
}

.field-hint {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
}

.try-no-params {
  font-size: 12.5px;
  color: var(--text-muted);
  margin-bottom: 12px;
}

.try-confirm {
  margin-top: 12px;
  background: var(--orange-bg);
  border: 1px solid var(--orange-border);
  border-radius: var(--radius-sm);
  padding: 12px 14px;
}

.try-confirm-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  font-weight: 700;
  color: #b45309;
  margin-bottom: 8px;
}

.try-confirm-params {
  margin: 0;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11.5px;
  color: var(--text);
  background: rgba(255, 255, 255, 0.7);
  border-radius: var(--radius-xs);
  padding: 10px;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 160px;
  overflow: auto;
}

.try-result {
  margin-top: 12px;
  border-radius: var(--radius-sm);
  padding: 12px 14px;
  font-size: 12.5px;
  display: none;
}

.try-result.show {
  display: block;
  animation: fadeIn 0.3s;
}

.try-result.ok {
  background: var(--green-bg);
  border: 1px solid var(--green-border);
  color: #047857;
}

.try-result.fail {
  background: var(--red-bg);
  border: 1px solid var(--red-border);
  color: var(--red);
}

.try-result pre {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11.5px;
  margin: 8px 0 0;
  color: var(--text);
  background: rgba(255, 255, 255, 0.6);
  padding: 10px;
  border-radius: var(--radius-xs);
  max-height: 200px;
  overflow: auto;
}

.try-meta {
  font-size: 10.5px;
  margin-top: 6px;
  opacity: 0.7;
}

.try-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.adv-debug {
  margin-top: 10px;
  border-top: 1px dashed var(--border);
  padding-top: 10px;
}

.adv-debug a {
  font-size: 11.5px;
  color: var(--text-muted);
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.adv-debug a:hover {
  color: var(--primary);
}

/* 抽屉底部操作栏 */
.drawer-foot {
  padding: 16px 24px;
  border-top: 1px solid var(--border-light);
  display: flex;
  gap: 10px;
  align-items: center;
  background: var(--surface);
}

.drawer-foot .btn-gradient {
  flex: 1;
  justify-content: center;
}

.agent-picker {
  position: relative;
  flex: 1;
}

.agent-menu {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 0;
  right: 0;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-lg);
  padding: 6px;
  display: none;
  z-index: 10;
  max-height: 280px;
  overflow-y: auto;
}

.agent-menu.show {
  display: block;
  animation: fadeIn 0.2s;
}

.agent-empty {
  padding: 14px 10px;
  font-size: 12.5px;
  color: var(--text-muted);
  text-align: center;
}

.agent-opt {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: var(--transition);
  font-size: 13px;
  font-weight: 600;
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  color: var(--text);
}

.agent-opt:hover {
  background: var(--primary-light);
}

.agent-opt .ao-icon {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-xs);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--indigo-bg);
  color: var(--primary);
}

.agent-opt small {
  display: block;
  font-size: 10.5px;
  color: var(--text-muted);
  font-weight: 500;
}

/* meta */
.meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.meta-item {
  background: var(--surface-alt);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
}

.meta-item .k {
  font-size: 10.5px;
  color: var(--text-muted);
  font-weight: 600;
}

.meta-item .v {
  font-size: 13px;
  font-weight: 700;
  margin-top: 2px;
  color: var(--text);
}

/* ============================================================
   Modal（编辑 / 调试 / 删除确认）
   ============================================================ */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.4);
  backdrop-filter: blur(4px);
  z-index: 2100;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

.modal-card {
  background: #FFFFFF;
  border-radius: var(--radius-lg);
  width: min(480px, 90vw);
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-xl);
  animation: modalIn 0.25s ease;
}

.modal-card-lg {
  width: min(680px, 92vw);
}

.modal-card-sm {
  width: min(420px, 90vw);
}

@keyframes modalIn {
  from { opacity: 0; transform: scale(0.95) translateY(16px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

.modal-header {
  padding: 18px 20px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-shrink: 0;
}

.modal-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
}

.modal-close {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  transition: var(--transition);
}

.modal-close:hover {
  background: var(--surface-alt);
  color: var(--text);
}

.modal-body {
  padding: 20px;
  overflow-y: auto;
}

.modal-footer {
  padding: 14px 20px;
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-shrink: 0;
}

.form-group {
  margin-bottom: 16px;
}

.form-group:last-child {
  margin-bottom: 0;
}

.form-row {
  display: flex;
  gap: 12px;
}

.form-group-half {
  flex: 1;
}

.form-label {
  display: block;
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 6px;
}

.required {
  color: var(--red);
}

.form-input {
  width: 100%;
  padding: 10px 12px;
  border: 1.5px solid var(--border);
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-family: inherit;
  color: var(--text);
  background: #FFFFFF;
  transition: var(--transition);
  outline: none;
  box-sizing: border-box;
}

.form-input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
}

.form-input::placeholder {
  color: var(--text-placeholder);
}

.form-textarea {
  resize: vertical;
  min-height: 60px;
  line-height: 1.6;
}

.form-hint {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
  line-height: 1.5;
}

/* 结构化 / 高级模式切换 */
.mode-switch {
  display: flex;
  gap: 4px;
  margin-bottom: 14px;
  padding: 3px;
  background: var(--surface-alt);
  border-radius: var(--radius-sm);
  width: fit-content;
}

.mode-link {
  padding: 6px 14px;
  border: none;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  background: transparent;
  cursor: pointer;
  transition: var(--transition);
}

.mode-link.active {
  background: #FFFFFF;
  color: var(--primary);
  box-shadow: var(--shadow-sm);
}

/* KV 行（请求头 / 参数表） */
.kv-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.kv-input {
  flex: 1;
}

.param-row .form-input {
  flex: 1;
}

.param-row .param-type {
  flex: 0 0 110px;
}

.param-required {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
  cursor: pointer;
}

.param-required input[type="checkbox"] {
  cursor: pointer;
}

.kv-remove {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: #FFFFFF;
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: var(--transition);
}

.kv-remove:hover {
  border-color: var(--red);
  color: var(--red);
  background: var(--red-bg);
}

.kv-add {
  padding: 6px 12px;
  border: 1px dashed var(--border);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: var(--transition);
}

.kv-add:hover {
  border-color: var(--primary-border);
  color: var(--primary);
  background: var(--indigo-bg);
}

.btn-secondary-custom {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 16px;
  border-radius: var(--radius);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  border: 1px solid var(--border);
  background: #FFFFFF;
  color: var(--text);
  transition: var(--transition);
  text-decoration: none;
}

.btn-secondary-custom:hover {
  border-color: var(--primary-border);
  box-shadow: var(--shadow-sm);
}

.btn-sm {
  padding: 6px 12px;
  font-size: 12px;
  border-radius: var(--radius-sm);
}

.btn-gradient:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-danger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: var(--radius);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  border: none;
  background: var(--red);
  color: #ffffff;
  transition: var(--transition);
  min-width: 88px;
}

.btn-danger:hover:not(:disabled) {
  background: #dc2626;
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3);
}

.btn-danger:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.confirm-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text);
}

.confirm-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--text);
  line-height: 1.6;
  margin: 0;
}

.confirm-text strong {
  font-weight: 700;
}

.confirm-hint {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.6;
  margin: 10px 0 0;
  padding: 10px 12px;
  background: var(--surface-alt);
  border-radius: var(--radius-sm);
  border-left: 2px solid var(--red);
}

/* 高级调试步骤 */
.debug-steps {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 18px;
}

.debug-step {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  border: 1.5px solid var(--border);
  color: var(--text-muted);
  background: #FFFFFF;
}

.debug-step.active {
  border-color: var(--primary);
  color: white;
  background: var(--primary);
}

.debug-step.done {
  border-color: var(--green);
  color: var(--green);
  background: var(--green-bg);
}

.debug-step-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-right: 10px;
}

/* Run / preview result */
.run-result {
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.run-result-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--surface-alt);
}

.run-latency {
  margin-left: auto;
  font-size: 12px;
  font-weight: 700;
  color: var(--primary);
  font-family: 'JetBrains Mono', monospace;
}

.run-result-body {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 240px;
  overflow-y: auto;
  font-family: 'JetBrains Mono', monospace;
}

.run-result-error {
  padding: 12px;
  font-size: 13px;
  color: var(--red);
  line-height: 1.6;
}

.run-result-errors {
  margin: 0;
  padding: 12px 12px 12px 28px;
  font-size: 13px;
  color: var(--red);
  line-height: 1.8;
}

.run-result-plan {
  padding: 12px;
  font-size: 13px;
  color: var(--text);
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.run-result-plan-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.run-result-trace {
  padding: 8px 12px;
  border-top: 1px solid var(--border-light);
  font-size: 11px;
  color: var(--text-muted);
}

/* ============================================================
   响应式
   ============================================================ */
@media (max-width: 1024px) {
  .list-head {
    display: none;
  }

  .skill-row {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .row-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .skill-grid {
    grid-template-columns: 1fr;
  }

  .drawer {
    width: 100vw;
  }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation: none !important;
    transition: none !important;
  }
}
</style>

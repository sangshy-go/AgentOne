<template>
  <div class="create-page">
    <!-- 步骤指示条 -->
    <div v-if="step < 4" class="steps">
      <div class="step" :class="stepCls(1)">
        <div class="step-num">{{ step > 1 ? '✓' : '1' }}</div>
        <div class="step-label">选择创建方式</div>
      </div>
      <div class="step-line" :class="{ done: step > 1 }"></div>
      <div class="step" :class="stepCls(2)">
        <div class="step-num">{{ step > 2 ? '✓' : '2' }}</div>
        <div class="step-label">填写内容</div>
      </div>
      <div class="step-line" :class="{ done: step > 2 }"></div>
      <div class="step" :class="stepCls(3)">
        <div class="step-num">3</div>
        <div class="step-label">预览并发布</div>
      </div>
    </div>

    <!-- ========== Step 1：选择创建方式 ========== -->
    <div v-if="step === 1">
      <h1 class="page-title">创建技能</h1>
      <p class="page-desc">选择最适合的方式。业务同学推荐用模板；外包 / 已有技能用「导入技能包」；IT 同学可接入已有系统。</p>
      <div class="entry-grid">
        <button
          v-for="e in ENTRIES"
          :key="e.key"
          type="button"
          class="entry-card"
          @click="pickEntry(e.key)"
        >
          <span v-if="e.tag" class="rec-tag">{{ e.tag }}</span>
          <span class="entry-ic" :style="{ background: e.icBg, color: e.icColor }" v-html="e.icon"></span>
          <span class="entry-title">{{ e.title }}</span>
          <span class="entry-desc">{{ e.desc }}</span>
          <span class="entry-for">{{ e.audience }}</span>
        </button>
      </div>
    </div>

    <!-- ========== Step 2：填写内容 ========== -->
    <div v-else-if="step === 2">
      <h1 class="page-title">{{ step2Title }}</h1>
      <p class="page-desc">{{ step2Desc }}</p>

      <div class="form-card">
        <!-- 模板库：模板选择器 -->
        <div v-if="entry === 'template'" class="field">
          <label>选择模板 <span class="req">*</span></label>
          <select v-model="selectedTemplate" class="input" @change="applyTemplate">
            <option value="">— 请选择 —</option>
            <option v-for="(t, i) in TEMPLATES" :key="i" :value="String(i)">
              [{{ t.cat }}] {{ t.name }} · {{ t.desc }}
            </option>
          </select>
        </div>

        <!-- 导入技能包：上传 / 粘贴 -->
        <template v-if="entry === 'import'">
          <div class="notice notice-info">
            <span v-html="ICONS.info"></span>
            <div>外包交付的技能通常是一个文件夹（SKILL.md + scripts/ + resources/）或 .zip 包；也可导入本系统导出的 .json 定义文件。直接导入即可，系统自动解析名称、简介与文件结构。</div>
          </div>

          <div class="field">
            <label>导入方式 <span class="req">*</span></label>
            <div class="btn-row">
              <label for="folderInput" class="btn-gradient file-label">
                <span v-html="ICONS.folder"></span> 选择技能文件夹
              </label>
              <label for="fileInput" class="btn-ghost file-label">
                <span v-html="ICONS.upload"></span> 上传 .zip / 单文件
              </label>
              <label for="jsonInput" class="btn-ghost file-label">
                <span v-html="ICONS.download"></span> 导入导出文件 .json
              </label>
            </div>
            <div class="hint">文件夹与压缩包为真实导入，系统自动解析 SKILL.md 并保存包内文件；导出文件按原定义还原。解析后可编辑。</div>
          </div>

          <!-- 导出文件 JSON：检测结论（无文件树，发布时按原定义导入） -->
          <template v-if="importKind === 'json' && jsonPayload">
            <div class="notice notice-info">
              <span v-html="ICONS.info"></span>
              <div>
                已识别为 <strong>导出文件技能</strong>：{{ jsonPayload.type === 'api' ? 'API 型' : '内容型' }}，版本 {{ jsonPayload.version || '1' }}。
                配置与参数按原定义导入，名称 / 简介 / 工种可在下方编辑。
              </div>
            </div>
            <div class="field">
              <button type="button" class="linklike" @click="clearJson">重新选择导出文件</button>
            </div>
          </template>

          <!-- 已选择文件：检测结论 + 文件树（只读，发布时原样上传） -->
          <template v-if="importKind !== 'paste' && pkgFiles.length > 0">
            <div class="notice" :class="pkgIsScript ? 'notice-info' : 'notice-info'">
              <span v-html="ICONS.info"></span>
              <div v-if="pkgIsScript">
                已识别为 <strong>脚本包技能</strong>：SKILL.md + {{ pkgFiles.length - 1 }} 个脚本 / 资源文件。
              </div>
              <div v-else>已识别为 <strong>内容型技能</strong>：仅含 SKILL.md，无脚本与资源。</div>
            </div>
            <div v-if="pkgIsScript" class="notice notice-warn">
              <span v-html="ICONS.warn"></span>
              <div><strong>脚本暂不执行：</strong>导入的脚本本期仅随包存储与分发，服务端执行将在沙箱能力上线后开放。</div>
            </div>
            <div class="field">
              <label>技能包文件结构（{{ pkgFiles.length }} 个文件）</label>
              <div class="file-split">
                <div class="file-tree">
                  <div class="file-tree-head">
                    <span class="fic" v-html="ICONS.folder"></span>
                    <span>文件</span>
                  </div>
                  <div class="file-tree-body">
                    <div
                      v-for="item in flatTree"
                      :key="item.node.path"
                      class="file-node"
                      :class="{
                        'is-dir': item.node.isDir,
                        selected: !item.node.isDir && previewFile?.path === item.node.path
                      }"
                      :style="{ paddingLeft: `${10 + item.depth * 16}px` }"
                      @click="item.node.isDir ? toggleDir(item.node.path) : (item.node.file && openPreview(item.node.file))"
                    >
                      <span v-if="item.node.isDir" class="chevron" :class="{ open: expandedDirs.has(item.node.path) }" v-html="ICONS.chevron"></span>
                      <span v-else class="chevron-spacer"></span>
                      <span class="fic" v-html="item.node.isDir ? ICONS.folder : fileIcon(item.node.path)"></span>
                      <span class="fname" :class="{ clickable: !item.node.isDir }">{{ item.node.name }}</span>
                      <span class="ftype">{{ item.node.isDir ? `${item.node.children.length} 项` : fileKindLabel(item.node.path) }}</span>
                    </div>
                  </div>
                </div>
                <div class="preview-panel">
                  <div v-if="previewFile" class="preview-head">
                    <span class="fic" v-html="fileIcon(previewFile.path)"></span>
                    <span class="preview-head-name">{{ previewFile.path.split('/').pop() }}</span>
                    <span class="preview-head-path">{{ previewFile.path }}</span>
                  </div>
                  <div class="preview-body">
                    <div v-if="!previewFile" class="preview-empty">
                      <div class="preview-empty-ic" v-html="ICONS.file"></div>
                      <div>点击左侧文件查看内容</div>
                    </div>
                    <div v-else-if="previewLoading" class="preview-state">加载中…</div>
                    <div v-else-if="previewError" class="preview-state error">{{ previewError }}</div>
                    <pre v-else class="preview-content">{{ previewText }}</pre>
                  </div>
                </div>
              </div>
              <button type="button" class="linklike" @click="clearPackage">重新选择</button>
            </div>
          </template>

          <!-- 粘贴 SKILL.md：用户已选文件夹/zip/导出文件时隐藏 -->
          <template v-if="pkgFiles.length === 0 && importKind !== 'json'">
            <div class="divider-or">或直接粘贴 SKILL.md 内容</div>
            <div class="field">
              <textarea
                v-model="pasteText"
                class="input textarea mono"
                rows="7"
                placeholder="---&#10;name: 客户跟进邮件&#10;description: 按客户阶段写跟进邮件&#10;---&#10;&#10;# 客户跟进邮件&#10;…"
              ></textarea>
              <div class="btn-row" style="margin-top: 10px;">
                <button type="button" class="btn-ghost" @click="parsePaste">
                  <span v-html="ICONS.check"></span> 解析粘贴内容
                </button>
              </div>
            </div>
          </template>
        </template>

        <!-- 基础字段：名称 / 简介 / 工种 / 正文（template / blank，以及 import 粘贴 / 导出文件解析后） -->
        <template v-if="showBaseFields">
          <div class="field">
            <label>技能名称 <span class="req">*</span></label>
            <input v-model="form.name" class="input" placeholder="如：客户跟进邮件" maxlength="100" />
          </div>
          <div class="field">
            <label>一句话简介 <span class="req">*</span></label>
            <input v-model="form.description" class="input" placeholder="这个技能能帮谁解决什么问题" maxlength="200" />
          </div>
          <div class="field">
            <label>所属工种 <span class="req">*</span></label>
            <div class="cat-select">
              <button
                v-for="c in SKILL_CATEGORIES"
                :key="c"
                type="button"
                class="cat-pill"
                :class="{ active: form.category === c }"
                @click="form.category = c"
              >{{ c }}</button>
            </div>
          </div>
          <!-- 导出文件导入时正文来自原定义 config，不可编辑 -->
          <div v-if="!(entry === 'import' && importKind === 'json')" class="field">
            <label>技能正文（Markdown）<span class="req">*</span></label>
            <textarea
              v-model="form.body"
              class="input textarea"
              rows="10"
              placeholder="把 SOP、规范、话术、方法论写在这里。Agent 调用技能时会按此执行…"
            ></textarea>
            <div class="hint">支持 Markdown。写得越具体，Agent 执行得越准。</div>
          </div>
        </template>

        <!-- API 接入 -->
        <template v-if="entry === 'api'">
          <div class="field">
            <label>技能名称 <span class="req">*</span></label>
            <input v-model="form.name" class="input" placeholder="如：征信查询" maxlength="100" />
          </div>
          <div class="field">
            <label>一句话简介 <span class="req">*</span></label>
            <input v-model="form.description" class="input" placeholder="这个接口封装后能做什么" maxlength="200" />
          </div>
          <div class="field">
            <label>所属工种 <span class="req">*</span></label>
            <div class="cat-select">
              <button
                v-for="c in SKILL_CATEGORIES"
                :key="c"
                type="button"
                class="cat-pill"
                :class="{ active: form.category === c }"
                @click="form.category = c"
              >{{ c }}</button>
            </div>
          </div>

          <div class="field-row">
            <div class="field">
              <label>请求地址 URL <span class="req">*</span></label>
              <input v-model="apiForm.url" class="input mono" placeholder="https://api.example.com/v1/query" />
              <div class="hint">仅允许 http/https，拒绝内网地址（SSRF 防护）。</div>
            </div>
            <div class="field">
              <label>请求方法</label>
              <select v-model="apiForm.method" class="input">
                <option>GET</option><option>POST</option><option>PUT</option><option>DELETE</option>
              </select>
            </div>
          </div>
          <div class="field-row">
            <div class="field">
              <label>超时时间（毫秒）</label>
              <input v-model.number="apiForm.timeout" class="input" type="number" min="1000" placeholder="10000" />
            </div>
          </div>

          <div class="field">
            <label>自定义请求头 <span class="opt">（可选，鉴权在此配置）</span></label>
            <div v-for="(h, i) in apiHeaders" :key="i" class="kv-row">
              <input v-model="h.key" class="input mono" placeholder="Header 名，如 Authorization" />
              <input v-model="h.value" class="input mono" placeholder="值，如 Bearer xxx" />
              <button type="button" class="del" title="删除" @click="apiHeaders.splice(i, 1)">
                <span v-html="ICONS.x"></span>
              </button>
            </div>
            <button type="button" class="add-file" @click="apiHeaders.push({ key: '', value: '' })">
              <span v-html="ICONS.plus"></span> 添加请求头
            </button>
          </div>

          <div class="field">
            <label>入参定义 <span class="opt">（可选，生成 JSON Schema）</span></label>
            <div v-for="(p, i) in apiParams" :key="i" class="param-row">
              <input v-model="p.name" class="input mono" placeholder="参数名，如 keyword" />
              <select v-model="p.type" class="input">
                <option>string</option><option>number</option><option>boolean</option>
              </select>
              <input v-model="p.description" class="input" placeholder="参数描述" />
              <label class="req-check"><input v-model="p.required" type="checkbox" /> 必填</label>
              <button type="button" class="del" title="删除" @click="apiParams.splice(i, 1)">
                <span v-html="ICONS.x"></span>
              </button>
            </div>
            <button type="button" class="add-file" @click="apiParams.push({ name: '', type: 'string', description: '', required: false })">
              <span v-html="ICONS.plus"></span> 添加参数
            </button>
          </div>
        </template>
      </div>

      <div class="form-actions">
        <button type="button" class="link-back" @click="backToStep1">
          <span v-html="ICONS.arrowLeft"></span> 重新选择
        </button>
        <button type="button" class="btn-ghost" @click="backToStep1">上一步</button>
        <button type="button" class="btn-gradient" :disabled="!canNext" @click="goPreview">
          下一步：预览 <span v-html="ICONS.arrowRight"></span>
        </button>
      </div>
    </div>

    <!-- ========== Step 3：预览并发布 ========== -->
    <div v-else-if="step === 3">
      <h1 class="page-title">预览并发布</h1>
      <p class="page-desc">确认技能信息无误后发布，发布后 Agent 即可绑定使用。</p>

      <div class="form-card">
        <div class="card-sec-label">技能卡片预览</div>
        <div class="preview-card">
          <span class="pv-icon" :style="{ background: previewTile + '22', color: previewTile }" v-html="previewIcon"></span>
          <div>
            <div class="pv-name">{{ previewName }}</div>
            <div class="pv-tagline">{{ previewDesc }}</div>
            <div class="pv-badges">
              <span class="badge badge-neutral">{{ form.category }}</span>
              <span class="badge badge-neutral">自建</span>
              <span v-if="previewIsScript" class="badge badge-script">脚本包</span>
              <span v-if="entry === 'api'" class="badge badge-info">API</span>
            </div>
          </div>
        </div>

        <div class="preview-body-wrap">
          <div class="card-sec-label">技能正文</div>
          <div class="preview-body">{{ previewBody }}</div>
        </div>
      </div>

      <div class="form-actions">
        <button type="button" class="btn-ghost" @click="step = 2">上一步</button>
        <button type="button" class="btn-gradient" :disabled="publishing" @click="doPublish">
          <span v-if="publishing" class="btn-spinner"></span>
          {{ publishing ? '发布中...' : '发布技能' }}
        </button>
      </div>
    </div>

    <!-- ========== 发布成功 ========== -->
    <div v-else class="success-wrap">
      <div class="success-ic" v-html="ICONS.checkLg"></div>
      <div class="success-title">技能发布成功</div>
      <div class="success-desc">「{{ publishedName }}」已加入技能库，现在可以到 Agent 详情页绑定使用。</div>
      <div class="success-actions">
        <router-link class="btn-ghost" :to="{ path: '/skills', query: { tab: 'mine' } }">查看我的技能</router-link>
        <router-link class="btn-gradient" :to="{ path: '/skills', query: { tab: 'plaza' } }">去广场看看</router-link>
      </div>
    </div>

    <!-- 隐藏文件输入：文件夹（真实）/ 单文件·zip（真实）/ 多文件（脚本包附加） -->
    <!-- 不用 display:none，走 visually-hidden：保留 label 原生点击传播，避免浏览器拦截 programmatic click -->
    <input id="folderInput" type="file" webkitdirectory directory multiple class="sr-file-input" @change="onFolderPick" />
    <input id="fileInput" type="file" accept=".zip,.md,.markdown,.txt" class="sr-file-input" @change="onFilePick" />
    <input id="jsonInput" type="file" accept=".json,application/json" class="sr-file-input" @change="onJsonPick" />

  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import JSZip from 'jszip'
import { useMessage } from 'naive-ui'
import {
  createSkill, importSkill, importSkillPackage, SKILL_CATEGORIES,
  type SkillForm, type SkillExportPayload,
} from '@/services/skill'

const message = useMessage()

type EntryKey = 'template' | 'blank' | 'import' | 'api'

/* ============================================================
   SVG 图标（内联，避免外部依赖）
   ============================================================ */
const ICONS = {
  info: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/></svg>',
  warn: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.3 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.7 3.86a2 2 0 00-3.4 0z"/><path d="M12 9v4M12 17h.01"/></svg>',
  folder: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z"/></svg>',
  upload: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M17 8l-5-5-5 5M12 3v12"/></svg>',
  download: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><path d="M7 10l5 5 5-5"/><path d="M12 15V3"/></svg>',
  check: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 6L9 17l-5-5"/></svg>',
  checkLg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M20 6L9 17l-5-5"/></svg>',
  x: '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6L6 18M6 6l12 12"/></svg>',
  plus: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>',
  arrowLeft: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>',
  arrowRight: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M12 5l7 7-7 7"/></svg>',
  doc: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><path d="M14 2v6h6M16 13H8M16 17H8"/></svg>',
  code: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M16 18l6-6-6-6M8 6l-6 6 6 6"/></svg>',
  file: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M13 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V9z"/><path d="M13 2v7h7"/></svg>',
  write: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 013 3L7 19l-4 1 1-4z"/></svg>',
  plug: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M9 2v6M15 2v6"/><path d="M6 8h12v4a6 6 0 01-6 6 6 6 0 01-6-6V8z"/><path d="M12 18v4"/></svg>',
  chevron: '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M9 18l6-6-6-6"/></svg>',
}

/* ============================================================
   Step 1：创建方式入口
   ============================================================ */
const ENTRIES: Array<{
  key: EntryKey
  title: string
  desc: string
  audience: string
  tag?: string
  icon: string
  icBg: string
  icColor: string
}> = [
  {
    key: 'template',
    title: '模板库',
    desc: '按工种精选的现成模板，选中即用，改改内容就能发布。',
    audience: '市场 / 销售 / 客服 / 人事…',
    tag: '业务推荐',
    icon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></svg>',
    icBg: 'linear-gradient(135deg,#EEF2FF,#E0E7FF)',
    icColor: '#6366F1',
  },
  {
    key: 'blank',
    title: '空白创建',
    desc: '从零沉淀团队 SOP、规范、话术，写成内容型技能。',
    audience: '想沉淀经验的业务同学',
    icon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 013 3L7 19l-4 1 1-4z"/></svg>',
    icBg: 'linear-gradient(135deg,#ECFDF5,#D1FAE5)',
    icColor: '#10B981',
  },
  {
    key: 'import',
    title: '导入技能包',
    desc: '导入已有的技能成品：整个文件夹、.zip 包，或本系统导出的 .json 定义文件。',
    audience: '外包交付 / 文件夹 / .zip / 导出文件',
    icon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z"/><path d="M12 11v6M9 14l3 3 3-3"/></svg>',
    icBg: 'linear-gradient(135deg,#ECFEFF,#CFFAFE)',
    icColor: '#06B6D4',
  },
  {
    key: 'api',
    title: 'API 接入',
    desc: '把已有 REST API 封装成技能，结构化表单配置。',
    audience: 'IT / 集成同学',
    icon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M9 2v6M15 2v6"/><path d="M6 8h12v4a6 6 0 01-6 6 6 6 0 01-6-6V8z"/><path d="M12 18v4"/></svg>',
    icBg: 'linear-gradient(135deg,#FFFBEB,#FEF3C7)',
    icColor: '#F59E0B',
  },
]

/* ============================================================
   模板库数据
   ============================================================ */
const TEMPLATES = [
  { cat: '市场', name: '小红书种草文案', desc: '按爆款结构生成种草文案' },
  { cat: '市场', name: '活动策划方案', desc: '按 5W2H 输出活动框架' },
  { cat: '销售', name: '客户跟进邮件', desc: '按客户阶段写跟进邮件' },
  { cat: '销售', name: '竞品对比分析', desc: '结构化输出竞品对比' },
  { cat: '客服', name: '客诉处理 SOP', desc: '安抚-定级-升级-回访' },
  { cat: '客服', name: '常见问题话术', desc: '高频问题标准答复' },
  { cat: '人事', name: '招聘 JD 生成', desc: '标准结构 + 规避歧视表述' },
  { cat: '人事', name: '面试题设计', desc: '按岗位能力模型出题' },
  { cat: '财务', name: '报销政策问答', desc: '报销标准与凭证要求' },
  { cat: '财务', name: '发票审核清单', desc: '发票合规逐项核查' },
  { cat: '法务合规', name: '合同审查清单', desc: '38 项风险点核查' },
  { cat: '行政', name: '会议纪要生成', desc: '要点-决议-待办三段式' },
  { cat: '行政', name: '周报生成助手', desc: '四段式周报整理' },
  { cat: '数据分析', name: '数据周报框架', desc: '指标-异动-归因-建议' },
]

/* ============================================================
   状态
   ============================================================ */
const step = ref(1)
const entry = ref<EntryKey | null>(null)
const publishing = ref(false)
const publishedName = ref('')

// 基础表单（template / blank / import-paste / api 共用 name/desc/category）
const form = ref({
  name: '',
  description: '',
  category: '市场',
  body: '',
})

// 模板
const selectedTemplate = ref('')

// API 表单
const apiForm = ref({ url: '', method: 'GET', timeout: 10000 })
interface HeaderRow { key: string; value: string }
interface ParamRow { name: string; type: string; description: string; required: boolean }
const apiHeaders = ref<HeaderRow[]>([])
const apiParams = ref<ParamRow[]>([])

// 技能包文件（import 文件夹 / zip）
interface PkgFile { file: File; path: string }
const pkgFiles = ref<PkgFile[]>([])
const pasteText = ref('')

/** 导出文件（.json）导入：解析后的原始定义，发布时走 importSkill */
const jsonPayload = ref<SkillExportPayload | null>(null)

/* 技能包文件树：扁平路径 → 树形结构，默认全部展开 */
interface PkgTreeNode { name: string; path: string; isDir: boolean; children: PkgTreeNode[]; file?: PkgFile }
const expandedDirs = ref<Set<string>>(new Set())

const pkgTree = computed<PkgTreeNode[]>(() => {
  const root: PkgTreeNode = { name: '', path: '', isDir: true, children: [] }
  for (const f of pkgFiles.value) {
    const parts = f.path.split('/').filter(Boolean)
    let cur = root
    let acc = ''
    for (let i = 0; i < parts.length; i++) {
      const part = parts[i]
      acc = acc ? `${acc}/${part}` : part
      const isLast = i === parts.length - 1
      if (isLast) {
        cur.children.push({ name: part, path: f.path, isDir: false, children: [], file: f })
      } else {
        let child = cur.children.find(c => c.isDir && c.name === part)
        if (!child) {
          child = { name: part, path: acc, isDir: true, children: [] }
          cur.children.push(child)
        }
        cur = child
      }
    }
  }
  const sort = (nodes: PkgTreeNode[]) => {
    nodes.sort((a, b) => a.isDir !== b.isDir ? (a.isDir ? -1 : 1) : a.name.localeCompare(b.name))
    nodes.forEach(n => { if (n.isDir) sort(n.children) })
  }
  sort(root.children)
  return root.children
})

interface FlatNode { node: PkgTreeNode; depth: number }
const flatTree = computed<FlatNode[]>(() => {
  const result: FlatNode[] = []
  const walk = (nodes: PkgTreeNode[], depth: number) => {
    for (const n of nodes) {
      result.push({ node: n, depth })
      if (n.isDir && expandedDirs.value.has(n.path)) walk(n.children, depth + 1)
    }
  }
  walk(pkgTree.value, 0)
  return result
})

watch(pkgFiles, (files) => {
  const allDirs = new Set<string>()
  for (const f of files) {
    const parts = f.path.split('/').filter(Boolean)
    let acc = ''
    for (let i = 0; i < parts.length - 1; i++) {
      acc = acc ? `${acc}/${parts[i]}` : parts[i]
      allDirs.add(acc)
    }
  }
  expandedDirs.value = allDirs
}, { immediate: true, deep: true })

function toggleDir(path: string) {
  const s = new Set(expandedDirs.value)
  if (s.has(path)) s.delete(path); else s.add(path)
  expandedDirs.value = s
}
/** import 的导入形态：folder/zip=真实上传；paste=解析后建内容型；json=导出文件按原定义导入 */
const importKind = ref<'folder' | 'zip' | 'paste' | 'json'>('folder')

/* ============================================================
   计算属性
   ============================================================ */
function stepCls(n: number) {
  return { active: step.value === n, done: step.value > n }
}

const step2Title = computed(() => {
  const map: Record<EntryKey, string> = {
    template: '从模板创建',
    blank: '空白创建',
    import: '导入技能包',
    api: 'API 接入',
  }
  return entry.value ? map[entry.value] : ''
})

const step2Desc = computed(() => {
  const map: Record<EntryKey, string> = {
    template: '选择一个模板，系统会预填内容骨架，你可继续修改。',
    blank: '把团队经验、SOP、规范写成内容型技能。',
    import: '导入外包或已有的技能成品：文件夹 / .zip 自动识别内容型或脚本包，导出文件（.json）按原定义还原。',
    api: '把已有 REST API 封装成技能。面向 IT / 集成场景，内置 SSRF 防护。',
  }
  return entry.value ? map[entry.value] : ''
})

/** 基础字段（名称/简介/工种/正文）何时展示 */
const showBaseFields = computed(() => {
  if (!entry.value) return false
  if (['template', 'blank'].includes(entry.value)) return true
  // import：粘贴 / 文件夹 / zip / 导出文件 解析后，均展示可编辑字段
  if (entry.value === 'import') {
    return pkgParsed.value
      && (importKind.value === 'paste' || importKind.value === 'folder' || importKind.value === 'zip' || importKind.value === 'json')
  }
  return false
})

/** 粘贴内容是否已解析 */
const pkgParsed = ref(false)

/** 导入 SKILL.md 解析出的原始 frontmatter 字段（扩展字段随包保留，发布时合并回 SKILL.md） */
const originalFrontmatter = ref<Record<string, string>>({})

/** 包内是否含脚本/资源（>1 个文件即视为脚本包） */
const pkgIsScript = computed(() => pkgFiles.value.length > 1)

/** 下一步是否可点 */
const canNext = computed(() => {
  if (!entry.value) return false
  if (entry.value === 'import') {
    // 真实上传需选中文件；粘贴需解析出名称；导出文件需解析成功且名称非空
    if (importKind.value === 'paste') return form.value.name.trim() !== '' && form.value.body.trim() !== ''
    if (importKind.value === 'json') return jsonPayload.value !== null && form.value.name.trim() !== ''
    return pkgFiles.value.length > 0
  }
  if (entry.value === 'api') {
    return form.value.name.trim() !== '' && form.value.description.trim() !== '' && /^https?:\/\//i.test(apiForm.value.url.trim())
  }
  // template / blank
  return form.value.name.trim() !== '' && form.value.description.trim() !== '' && form.value.body.trim() !== ''
})

/* ============================================================
   预览
   ============================================================ */
const CAT_TILE: Record<string, string> = {
  市场: '#F59E0B', 销售: '#3B82F6', 客服: '#06B6D4', 人事: '#6366F1', 财务: '#10B981',
  法务合规: '#8B5CF6', 行政: '#10B981', 数据分析: '#3B82F6', IT集成: '#F59E0B', 其他: '#64748B',
}
const previewTile = computed(() => CAT_TILE[form.value.category] || '#6366F1')
const previewName = computed(() => form.value.name || '未命名技能')
const previewDesc = computed(() => form.value.description || '（暂无简介）')
const previewBody = computed(() => {
  if (entry.value === 'import' && importKind.value === 'json') {
    return '（来自导出文件 .json，配置与参数 schema 按原定义还原）'
  }
  if (entry.value === 'import' && importKind.value !== 'paste') {
    return `（技能正文来自包内 SKILL.md，共 ${pkgFiles.value.length} 个文件将一并导入）`
  }
  return form.value.body || '（暂无正文）'
})
const previewIsScript = computed(() => {
  if (entry.value === 'import') return pkgIsScript.value
  return false
})
const previewIcon = computed(() => {
  if (entry.value === 'api') return ICONS.plug
  if (previewIsScript.value) return ICONS.code
  return ICONS.write
})

/* ============================================================
   Step 流转
   ============================================================ */
function pickEntry(key: EntryKey) {
  entry.value = key
  resetEntryState()
  step.value = 2
  window.scrollTo({ top: 0 })
}

function resetEntryState() {
  form.value = { name: '', description: '', category: '市场', body: '' }
  selectedTemplate.value = ''
  apiForm.value = { url: '', method: 'GET', timeout: 10000 }
  apiHeaders.value = []
  apiParams.value = [{ name: '', type: 'string', description: '', required: false }]
  pkgFiles.value = []
  pasteText.value = ''
  pkgParsed.value = false
  importKind.value = 'folder'
  originalFrontmatter.value = {}
  jsonPayload.value = null
}

function backToStep1() {
  step.value = 1
  window.scrollTo({ top: 0 })
}

function goPreview() {
  if (!canNext.value) return
  step.value = 3
  window.scrollTo({ top: 0 })
}

/* ============================================================
   模板应用
   ============================================================ */
function applyTemplate() {
  if (selectedTemplate.value === '') return
  const t = TEMPLATES[Number(selectedTemplate.value)]
  form.value.name = t.name
  form.value.description = t.desc
  form.value.category = t.cat
  form.value.body = `# ${t.name}\n\n## 目标\n${t.desc}\n\n## 执行步骤\n1. \n2. \n3. \n\n## 输出规范\n- \n\n## 注意事项\n- `
  message.success(`已应用模板「${t.name}」`)
}

/* ============================================================
   文件选择（真实上传前的本地暂存）
   ============================================================ */

/** 把 File 列表转成 {file, path}；folder 场景剥掉顶层目录名 */
function toPkgFiles(files: File[], stripRoot: boolean): PkgFile[] {
  return files
    .map((f) => {
      const rel = (f as File & { webkitRelativePath?: string }).webkitRelativePath || f.name
      const path = stripRoot ? rel.split('/').slice(1).join('/') || f.name : rel
      return { file: f, path }
    })
    .filter((f) => f.path !== '')
    .sort((a, b) => a.path.localeCompare(b.path))
}

async function onFolderPick(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length) return

  // 导入技能包：SKILL.md 是必需入口
  const skillMdFile = files.find((f) => /(^|\/)skill\.md$/i.test(f.name))
  if (!skillMdFile) {
    message.error('未找到 SKILL.md，请确认技能包结构')
    return
  }
  pkgFiles.value = toPkgFiles(files, true)
  importKind.value = 'folder'
  // 解析 SKILL.md 前置信息到表单，允许用户在发布前编辑
  await prefillFromSkillMd(skillMdFile)
  message.success(`已选择 ${pkgFiles.value.length} 个文件`)
}

/** 读取 SKILL.md 文本 → 解析 frontmatter → 回填表单字段 */
async function prefillFromSkillMd(file: File) {
  try {
    const text = await file.text()
    const parsed = parseFrontmatter(text)
    if (parsed.name) form.value.name = parsed.name
    if (parsed.desc) form.value.description = parsed.desc
    if (parsed.body) form.value.body = parsed.body.trim()
    applyCategory(parsed.cat)
    originalFrontmatter.value = parsed.raw
    pkgParsed.value = true
  } catch {
    // 解析失败不阻塞导入，用户手动填
  }
}

async function onFilePick(e: Event) {
  const input = e.target as HTMLInputElement
  const f = input.files?.[0]
  input.value = ''
  if (!f) return
  if (/\.zip$/i.test(f.name)) {
    // zip：前端解压，展示文件树
    try {
      const zip = await JSZip.loadAsync(f)
      const extracted: PkgFile[] = []
      for (const [path, zipEntry] of Object.entries(zip.files)) {
        if (zipEntry.dir) continue
        // 跳过 macOS Finder 压缩时自动生成的资源 fork 文件
        if (isMacOsResourceFork(path)) continue
        const blob = await zipEntry.async('blob')
        const fileName = path.split('/').pop() || path
        const file = new File([blob], fileName, { type: '' })
        extracted.push({ file, path })
      }
      pkgFiles.value = stripCommonRoot(extracted)
      // 解析 SKILL.md 回填表单
      const skillMd = pkgFiles.value.find(p => /(^|\/)skill\.md$/i.test(p.path))
      if (skillMd) {
        const text = await skillMd.file.text()
        const parsed = parseFrontmatter(text)
        if (parsed.name) form.value.name = parsed.name
        if (parsed.desc) form.value.description = parsed.desc
        if (parsed.body) form.value.body = parsed.body.trim()
        applyCategory(parsed.cat)
        originalFrontmatter.value = parsed.raw
        pkgParsed.value = true
      } else {
        message.warning('zip 内未找到 SKILL.md，请手动填写技能信息')
      }
      if (entry.value === 'import') importKind.value = 'zip'
      message.success(`已解压 ${f.name}，包含 ${pkgFiles.value.length} 个文件`)
    } catch (err) {
      console.error('zip 解压失败', err)
      message.error('zip 解压失败，请确认文件格式')
    }
  } else {
    // 单个 .md：读文本解析 frontmatter，作为内容型处理
    const text = await f.text()
    const parsed = parseFrontmatter(text)
    if (parsed.name) form.value.name = parsed.name
    if (parsed.desc) form.value.description = parsed.desc
    if (parsed.body) form.value.body = parsed.body.trim()
    applyCategory(parsed.cat)
    originalFrontmatter.value = parsed.raw
    pkgFiles.value = [{ file: f, path: 'SKILL.md' }]
    if (entry.value === 'import') importKind.value = 'paste'
    pkgParsed.value = true
    message.success(`已导入 ${f.name}`)
  }
}

/** 导出文件（.json）：解析原始定义并回填表单，发布时走 importSkill */
async function onJsonPick(e: Event) {
  const input = e.target as HTMLInputElement
  const f = input.files?.[0]
  input.value = ''
  if (!f) return
  let payload: SkillExportPayload
  try {
    payload = JSON.parse(await f.text()) as SkillExportPayload
  } catch {
    message.error('文件不是合法 JSON，请确认是本系统导出的技能文件')
    return
  }
  // 与后端 SkillImportDTO 校验对齐：name / config 必填
  if (!payload || typeof payload !== 'object' || !payload.name || !payload.config) {
    message.error('不是有效的技能导出文件（缺少 name / config 字段）')
    return
  }
  jsonPayload.value = payload
  pkgFiles.value = []
  form.value.name = payload.name
  form.value.description = payload.description || ''
  applyCategory(payload.category || '')
  importKind.value = 'json'
  pkgParsed.value = true
  message.success(`已解析导出文件「${payload.name}」`)
}

function clearJson() {
  jsonPayload.value = null
  pkgParsed.value = false
  importKind.value = 'folder'
  originalFrontmatter.value = {}
}

/** 判断是否为 macOS Finder 压缩时自动生成的资源 fork 文件 */
function isMacOsResourceFork(path: string): boolean {
  if (path.startsWith('__MACOSX/') || path.includes('/__MACOSX/')) return true
  const fileName = path.split('/').pop() || path
  return fileName.startsWith('._')
}

/** zip 整包有公共根目录时剥离，保证 SKILL.md 在根 */
function stripCommonRoot(files: PkgFile[]): PkgFile[] {
  if (files.length < 2) return files
  const first = files[0].path
  const slash = first.indexOf('/')
  if (slash <= 0) return files
  const root = first.substring(0, slash + 1)
  const allSameRoot = files.every(f => f.path.startsWith(root))
  if (!allSameRoot) return files
  return files
    .map(f => ({ file: f.file, path: f.path.substring(root.length) }))
    .filter(f => f.path !== '')
}

function clearPackage() {
  pkgFiles.value = []
  if (entry.value === 'import') importKind.value = 'folder'
  if (previewFile.value && !pkgFiles.value.some(f => f.path === previewFile.value?.path)) {
    previewFile.value = null
    previewText.value = ''
  }
}

/* 文件预览：点击文件名触发，模态框展示文本内容 */
const previewFile = ref<PkgFile | null>(null)
const previewText = ref<string>('')
const previewLoading = ref(false)
const previewError = ref<string>('')

async function openPreview(file: PkgFile) {
  previewFile.value = file
  previewText.value = ''
  previewError.value = ''
  previewLoading.value = true
  try {
    if (!isProbablyText(file.path)) {
      previewError.value = '该文件类型暂不支持预览'
      return
    }
    const text = await file.file.text()
    previewText.value = text
  } catch {
    previewError.value = '读取文件失败'
  } finally {
    previewLoading.value = false
  }
}

/** 粗判是否为文本文件（与后端 isProbablyText 对齐） */
function isProbablyText(path: string): boolean {
  const ext = path.split('.').pop()?.toLowerCase() || ''
  const textExts = ['md', 'txt', 'json', 'yaml', 'yml', 'xml', 'py', 'js', 'ts', 'jsx', 'tsx',
    'sh', 'bash', 'zsh', 'rb', 'go', 'php', 'java', 'kt', 'scala', 'c', 'cpp', 'h', 'hpp',
    'css', 'scss', 'less', 'html', 'htm', 'vue', 'svelte', 'sql', 'toml', 'ini', 'cfg', 'conf',
    'env', 'gitignore', 'dockerfile', 'makefile', 'csv', 'log', 'rst']
  if (textExts.includes(ext)) return true
  const base = path.split('/').pop()?.toLowerCase() || ''
  return ['makefile', 'dockerfile', 'rakefile', 'gemfile'].includes(base)
}

/* ============================================================
   粘贴解析（import-paste）
   ============================================================ */
function parseFrontmatter(text: string): { name: string; desc: string; cat: string; body: string; raw: Record<string, string> } {
  let name = ''
  let desc = ''
  let cat = ''
  let body = text || ''
  // raw: 完整 frontmatter 字段映射（保留 scripts/resources/permissions 等扩展字段）
  const raw: Record<string, string> = {}
  const m = (text || '').match(/^---\n([\s\S]*?)\n---\n?/)
  if (m) {
    body = (text || '').slice(m[0].length)
    for (const line of m[1].split('\n')) {
      const fm = line.match(/^([A-Za-z0-9_-]+):\s*(.*)$/)
      if (!fm) continue
      const key = fm[1].trim()
      let val = fm[2].trim()
      // 去掉包裹的引号，避免重建时重复转义
      if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
        val = val.slice(1, -1)
      }
      raw[key] = val
      if (key === 'name') name = val
      else if (key === 'description') desc = val
      else if (key === 'category') cat = val
    }
  } else {
    const first = (text || '').split('\n').find((l) => l.trim())
    name = (first || '').replace(/^#+\s*/, '')
  }
  return { name, desc, cat, body, raw }
}

/** frontmatter category 落在受控词表内才采纳，否则保持默认 */
function applyCategory(cat: string) {
  if (cat && (SKILL_CATEGORIES as readonly string[]).includes(cat)) {
    form.value.category = cat
  }
}

function parsePaste() {
  const raw = pasteText.value.trim()
  if (!raw) {
    message.warning('请先粘贴内容')
    return
  }
  const parsed = parseFrontmatter(raw)
  form.value.name = parsed.name
  form.value.description = parsed.desc
  form.value.body = parsed.body.trim()
  applyCategory(parsed.cat)
  originalFrontmatter.value = parsed.raw
  importKind.value = 'paste'
  pkgParsed.value = true
  message.success('解析成功')
}

/* ============================================================
   文件树展示辅助
   ============================================================ */
function fileIcon(path: string): string {
  if (/\.(py|js|ts|sh|rb|go|php)$/i.test(path) || /(^|\/)scripts\//i.test(path)) return ICONS.code
  if (/\.md$/i.test(path)) return ICONS.doc
  return ICONS.file
}
function fileKindLabel(path: string): string {
  if (/(^|\/)skill\.md$/i.test(path)) return '说明 · 必需'
  if (/\.(py|js|ts|sh|rb|go|php)$/i.test(path) || /(^|\/)scripts\//i.test(path)) return '脚本'
  if (/\.md$/i.test(path)) return '文档'
  return '资源'
}

/* ============================================================
   API 配置构造（与 SkillCenterPage 的 buildApiConfig 保持一致）
   ============================================================ */
function buildApiConfig(): Record<string, unknown> {
  const headers: Record<string, string> = {}
  for (const h of apiHeaders.value) {
    if (h.key.trim()) headers[h.key.trim()] = h.value
  }
  const config: Record<string, unknown> = {
    url: apiForm.value.url.trim(),
    method: apiForm.value.method,
    timeout: Number(apiForm.value.timeout) > 0 ? Number(apiForm.value.timeout) : 10000,
  }
  if (Object.keys(headers).length > 0) config.headers = headers
  return config
}

function buildInputSchema(): string | undefined {
  const properties: Record<string, Record<string, unknown>> = {}
  const required: string[] = []
  for (const p of apiParams.value) {
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

/** 由表单生成 SKILL.md（frontmatter + 正文），用于脚本包导入 */
function buildSkillMd(): string {
  // 合并导入时解析出的原始 frontmatter（保留 scripts/resources/permissions 等扩展字段），
  // 再用用户编辑后的名称/简介/工种覆盖，实现扩展字段的 round-trip 保留。
  const fmMap: Record<string, string> = { ...originalFrontmatter.value }
  fmMap.name = form.value.name.trim()
  fmMap.description = form.value.description.trim()
  fmMap.category = form.value.category
  const fm = ['---']
  for (const [k, v] of Object.entries(fmMap)) {
    fm.push(`${k}: ${v}`)
  }
  fm.push('---', '')
  return fm.join('\n') + form.value.body.trim() + '\n'
}

/* ============================================================
   发布（真实写后端）
   ============================================================ */
async function doPublish() {
  if (!entry.value) return
  publishing.value = true
  try {
    let created: string

    if (entry.value === 'import' && importKind.value === 'json') {
      // 导出文件导入：原定义 + 用户编辑的名称/简介/工种覆盖
      if (!jsonPayload.value) throw new Error('请先选择导出文件')
      const res = await importSkill({
        ...jsonPayload.value,
        name: form.value.name.trim(),
        description: form.value.description.trim(),
        category: form.value.category,
      })
      created = res.data.data?.name || form.value.name
    } else if (entry.value === 'import' && importKind.value !== 'paste') {
      // 真实技能包上传（文件夹 / zip 统一走文件列表上传）
      const skillMdFile = new File([buildSkillMd()], 'SKILL.md', { type: 'text/markdown' })
      const others = pkgFiles.value.filter((f) => f.path.toLowerCase() !== 'skill.md')
      const files = [skillMdFile, ...others.map((f) => f.file)]
      const paths = ['SKILL.md', ...others.map((f) => f.path)]
      const res = await importSkillPackage({ files, paths })
      created = res.data.data?.name || form.value.name
    } else if (entry.value === 'api') {
      const payload: SkillForm = {
        name: form.value.name.trim(),
        type: 'api',
        category: form.value.category,
        description: form.value.description.trim() || undefined,
        config: JSON.stringify(buildApiConfig()),
        inputSchema: buildInputSchema(),
      }
      const res = await createSkill(payload)
      created = res.data.data?.name || form.value.name
    } else {
      // template / blank / import-paste → 内容型 prompt Skill
      const payload: SkillForm = {
        name: form.value.name.trim(),
        type: 'prompt',
        category: form.value.category,
        description: form.value.description.trim() || undefined,
        config: JSON.stringify({ content: form.value.body }),
      }
      const res = await createSkill(payload)
      created = res.data.data?.name || form.value.name
    }

    publishedName.value = created
    step.value = 4
    window.scrollTo({ top: 0 })
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '发布失败')
  } finally {
    publishing.value = false
  }
}
</script>

<style scoped>
.create-page {
  max-width: 1000px;
  margin: 0 auto;
  animation: pageIn 0.4s ease;
}

/* ---------- 步骤条 ---------- */
.steps {
  display: flex;
  align-items: center;
  margin-bottom: 28px;
}
.step {
  display: flex;
  align-items: center;
  gap: 9px;
}
.step-num {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2px solid var(--border-strong);
  color: var(--text-muted);
  font-weight: 800;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #FFFFFF;
  transition: var(--transition);
}
.step-label {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-muted);
  transition: var(--transition);
}
.step.active .step-num {
  background: var(--grad-primary-2);
  color: #fff;
  border-color: transparent;
  box-shadow: var(--glow-primary);
}
.step.active .step-label { color: var(--text); }
.step.done .step-num { background: var(--green); color: #fff; border-color: transparent; }
.step.done .step-label { color: var(--text-secondary); }
.step-line {
  flex: 1;
  height: 2px;
  background: var(--border);
  margin: 0 14px;
  border-radius: 1px;
  min-width: 20px;
}
.step-line.done { background: var(--green); }

/* ---------- 入口卡片 ---------- */
.entry-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}
.entry-card {
  background: #FFFFFF;
  border: 1.5px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 20px;
  cursor: pointer;
  transition: var(--transition);
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  text-align: left;
  font-family: inherit;
}
.entry-card:hover {
  border-color: var(--primary);
  box-shadow: var(--shadow-md), var(--glow-primary);
  transform: translateY(-2px);
}
.entry-ic {
  width: 44px;
  height: 44px;
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: center;
}
.entry-title { font-weight: 800; font-size: 15px; letter-spacing: -0.2px; color: var(--text); }
.entry-desc { font-size: 12.5px; color: var(--text-secondary); line-height: 1.55; }
.entry-for { margin-top: auto; font-size: 11px; font-weight: 700; color: var(--text-muted); }
.rec-tag {
  position: absolute;
  top: -9px;
  right: 14px;
  background: var(--grad-primary-2);
  color: #fff;
  font-size: 10px;
  font-weight: 800;
  padding: 3px 10px;
  border-radius: 9999px;
  box-shadow: var(--glow-primary);
}

/* ---------- 表单卡片 ---------- */
.form-card {
  background: #FFFFFF;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 24px;
  box-shadow: var(--shadow);
}
.field { margin-bottom: 18px; }
.field:last-child { margin-bottom: 0; }
.field label {
  display: block;
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 7px;
}
.req { color: var(--red); }
.opt { color: var(--text-placeholder); font-weight: 500; font-size: 11px; }
.input {
  width: 100%;
  padding: 10px 13px;
  border-radius: var(--radius-sm);
  border: 1.5px solid var(--border);
  font-size: 13.5px;
  font-family: inherit;
  outline: none;
  transition: var(--transition);
  background: #FFFFFF;
  color: var(--text);
  box-sizing: border-box;
}
.input:focus { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12); }
.input::placeholder { color: var(--text-placeholder); }
select.input { cursor: pointer; }
.textarea { resize: vertical; line-height: 1.6; min-height: 80px; }
.hint { font-size: 11.5px; color: var(--text-muted); margin-top: 6px; }
.field-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.mono { font-family: 'JetBrains Mono', 'Fira Code', monospace; }

.cat-select { display: flex; gap: 8px; flex-wrap: wrap; }
.cat-pill {
  padding: 7px 15px;
  border-radius: 9999px;
  border: 1.5px solid var(--border);
  background: #FFFFFF;
  font-size: 12.5px;
  font-weight: 700;
  color: var(--text-secondary);
  cursor: pointer;
  transition: var(--transition);
  font-family: inherit;
}
.cat-pill:hover { border-color: var(--primary-border); }
.cat-pill.active {
  background: var(--grad-primary-2);
  color: #fff;
  border-color: transparent;
  box-shadow: var(--glow-primary);
}

/* ---------- 提示条 ---------- */
.notice {
  display: flex;
  gap: 11px;
  padding: 13px 15px;
  border-radius: var(--radius);
  font-size: 12.5px;
  line-height: 1.55;
  margin-bottom: 18px;
  align-items: flex-start;
}
.notice span { width: 16px; height: 16px; flex-shrink: 0; margin-top: 2px; display: inline-flex; }
.notice-info { background: var(--indigo-bg); border: 1px solid var(--indigo-border); color: #3730A3; }
.notice-warn { background: var(--orange-bg); border: 1px solid var(--orange-border); color: #92400E; }

/* ---------- 文件树 ---------- */
.file-tree {
  background: var(--surface, #FFFFFF);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 0;
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.file-tree-head {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 14px;
  background: #F8FAFC;
  border-bottom: 1px solid var(--border);
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  flex-shrink: 0;
}
.file-tree-head .fic { color: var(--text-muted); }
.file-tree-body {
  padding: 6px;
  overflow: auto;
  flex: 1;
}
.file-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: var(--radius-xs);
  font-size: 12.5px;
  transition: background 120ms ease;
  position: relative;
  cursor: default;
}
.file-node:hover { background: #F1F5F9; }
.file-node.is-dir { cursor: pointer; }
.file-node.is-dir .fname { color: var(--text); font-weight: 600; }
.chevron { width: 12px; height: 12px; flex-shrink: 0; display: inline-flex; align-items: center; justify-content: center; transition: transform 150ms ease; color: var(--text-placeholder); }
.chevron.open { transform: rotate(90deg); }
.chevron-spacer { width: 12px; flex-shrink: 0; }
.fic { width: 14px; height: 14px; color: var(--text-muted); flex-shrink: 0; display: inline-flex; }
.fname.clickable { cursor: pointer; }
.fname.clickable:hover { color: var(--primary, #2563EB); }

/* ---------- 文件预览（左右分栏） ---------- */
.file-split {
  display: flex;
  gap: 16px;
  align-items: stretch;
  height: min(80vh, 900px);
  min-height: 480px;
}
.file-split > .file-tree {
  flex: 0 0 240px;
  min-width: 0;
  margin-bottom: 0;
  height: 100%;
}
.preview-panel {
  flex: 1;
  min-width: 0;
  background: #F6F8FA;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}
.preview-head {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 16px;
  background: #EEF2F7;
  border-bottom: 1px solid var(--border);
  font-family: 'JetBrains Mono', monospace;
  font-size: 12.5px;
  color: var(--text);
  flex-shrink: 0;
}
.preview-head .fic { flex-shrink: 0; color: var(--text-muted); }
.preview-head-name { font-weight: 600; }
.preview-head-path {
  color: var(--text-placeholder); font-size: 11.5px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  margin-left: auto;
}
.preview-body {
  flex: 1; overflow: auto; padding: 16px 20px;
  background: #F6F8FA;
}
.preview-empty {
  height: 100%; min-height: 240px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 12px;
  color: var(--text-placeholder);
  font-size: 12.5px;
}
.preview-empty-ic {
  width: 48px; height: 48px;
  display: inline-flex; align-items: center; justify-content: center;
  border-radius: 12px;
  background: var(--surface, #FFFFFF);
  border: 1.5px dashed var(--border-strong);
  color: var(--text-muted);
}
.preview-empty-ic svg { width: 22px; height: 22px; }
.preview-state { display: flex; align-items: center; justify-content: center; color: var(--text-muted); padding: 40px 0; font-size: 13px; }
.preview-state.error { color: var(--danger); }
.preview-content {
  margin: 0;
  padding: 14px 16px;
  background: var(--surface, #FFFFFF);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px; line-height: 1.7;
  color: var(--text);
  white-space: pre-wrap; word-break: break-word;
  tab-size: 2;
  min-height: 100%;
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.02);
}
.file-node.selected {
  background: #E8F0FE;
}
.file-node.selected::before {
  content: '';
  position: absolute;
  left: 0; top: 4px; bottom: 4px;
  width: 3px;
  background: var(--primary, #2563EB);
  border-radius: 1.5px;
}
.file-node.selected .fname { color: var(--primary, #2563EB); font-weight: 600; }
.file-node.selected .fic { color: var(--primary, #2563EB); }
.fname { font-family: 'JetBrains Mono', monospace; font-weight: 600; word-break: break-all; }
.ftype { margin-left: auto; font-size: 10.5px; color: var(--text-placeholder); white-space: nowrap; }
.ftype.req-ok { color: var(--green); }
.del {
  width: 22px;
  height: 22px;
  border-radius: 5px;
  border: none;
  background: transparent;
  color: var(--text-placeholder);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: var(--transition);
  flex-shrink: 0;
}
.del:hover { background: var(--red-bg); color: var(--red); }

.add-file {
  width: 100%;
  margin-top: 8px;
  padding: 9px;
  border-radius: var(--radius-sm);
  border: 1.5px dashed var(--border-strong);
  background: transparent;
  color: var(--text-muted);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  transition: var(--transition);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-family: inherit;
}
.add-file:hover { border-color: var(--primary); color: var(--primary); background: var(--primary-light); }

.linklike {
  margin-top: 8px;
  background: none;
  border: none;
  color: var(--primary);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  font-family: inherit;
}
.linklike:hover { text-decoration: underline; }

.divider-or {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--text-placeholder);
  font-size: 12px;
  margin: 18px 0;
}
.divider-or::before, .divider-or::after { content: ''; flex: 1; height: 1px; background: var(--border); }

/* ---------- API 行 ---------- */
.kv-row {
  display: grid;
  grid-template-columns: 1fr 1.4fr 34px;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}
.param-row {
  display: grid;
  grid-template-columns: 1fr 110px 1.4fr 70px 34px;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}
.req-check {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: var(--text-muted);
  white-space: nowrap;
  font-weight: 500;
}

/* ---------- 底部操作 ---------- */
.form-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 24px;
  align-items: center;
}
.btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 10px 18px;
  border-radius: var(--radius);
  font-size: 13px;
  font-weight: 700;
  border: 1px solid var(--border);
  background: #FFFFFF;
  color: var(--text-secondary);
  cursor: pointer;
  transition: var(--transition);
  white-space: nowrap;
  text-decoration: none;
  font-family: inherit;
}
.btn-ghost:hover { border-color: var(--primary-border); color: var(--primary); }
.btn-gradient:disabled, .btn-ghost:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }
.btn-row { display: flex; gap: 10px; flex-wrap: wrap; }
.link-back {
  margin-right: auto;
  color: var(--text-muted);
  background: none;
  border: none;
  font-size: 13px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-family: inherit;
}
.link-back:hover { color: var(--primary); }
.btn-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ---------- 预览 ---------- */
.card-sec-label {
  font-size: 13px;
  font-weight: 800;
  color: var(--text-muted);
  letter-spacing: 0.5px;
  margin-bottom: 14px;
}
.preview-card {
  display: flex;
  gap: 14px;
  background: #FFFFFF;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 18px;
  max-width: 460px;
}
.pv-icon {
  width: 46px;
  height: 46px;
  border-radius: var(--radius);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.pv-name { font-weight: 800; font-size: 15px; color: var(--text); }
.pv-tagline { font-size: 12.5px; color: var(--text-secondary); margin-top: 3px; line-height: 1.5; }
.pv-badges { display: flex; gap: 6px; margin-top: 8px; flex-wrap: wrap; }
.badge-script {
  background: var(--orange-bg);
  color: var(--orange);
  border-color: var(--orange-border);
}
.preview-body-wrap { margin-top: 20px; padding-top: 20px; border-top: 1px solid var(--border-light); }
/* 限定作用域：步骤三的正文预览，避免 max-height 污染文件分栏的 .preview-body */
.preview-body-wrap .preview-body {
  background: var(--surface-alt);
  border: 1px solid var(--border-light);
  border-radius: var(--radius);
  padding: 16px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--text-secondary);
  white-space: pre-wrap;
  max-height: 240px;
  overflow: auto;
}

/* ---------- 成功页 ---------- */
.success-wrap { text-align: center; padding: 60px 20px; animation: pageIn 0.35s ease; }
.success-ic {
  width: 76px;
  height: 76px;
  border-radius: 50%;
  background: var(--green-bg);
  border: 1.5px solid var(--green-border);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
  color: var(--green);
}
.success-title { font-size: 22px; font-weight: 800; margin-bottom: 8px; color: var(--text); }
.success-desc { font-size: 13.5px; color: var(--text-muted); margin-bottom: 26px; }
.success-actions { display: flex; gap: 10px; justify-content: center; flex-wrap: wrap; }

/* 文件输入的 visually-hidden：不占空间，但 label[for] 仍可原生点击触发 */
.sr-file-input {
  position: absolute;
  width: 1px; height: 1px;
  padding: 0; margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
/* label 模拟按钮：继承 btn-gradient / btn-ghost / add-file 样式，光标变手型 */
.file-label, .add-file.file-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  user-select: none;
  text-decoration: none;
}
.file-label:hover { filter: brightness(0.97); }

/* ---------- 响应式 / 降级 ---------- */
/* 窄屏：文件树与预览面板纵向堆叠，避免预览被挤压过窄 */
@media (max-width: 1100px) {
  .file-split {
    flex-direction: column;
    height: auto;
    min-height: 0;
  }
  .file-split > .file-tree {
    flex: 0 0 auto;
    width: 100%;
    height: 240px;
  }
  /* 纵向为主轴：flex:1 的 basis 0 会覆盖 height，须改 none */
  .preview-panel {
    flex: none;
    width: 100%;
    height: 420px;
  }
}
@media (max-width: 640px) {
  .entry-grid { grid-template-columns: 1fr; }
  .field-row { grid-template-columns: 1fr; }
  .step-label { display: none; }
  .param-row { grid-template-columns: 1fr 1fr; }
}
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation: none !important; transition: none !important; }
}
</style>

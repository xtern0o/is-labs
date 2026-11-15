<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { computed, onMounted, ref, watch } from 'vue'

import danger from '@/assets/img/danger-18465_256.gif'
import radioactive from '@/assets/img/radioactive-19946_256.gif'
import { apiDownload, apiGet, apiPostFile } from '@/config/api'
import { showError } from '@/shared/errors'
import { useAuthStore } from '@/stores/auth'
import { useDatabaseStore } from '@/stores/database'

type ImportStatus =
  | 'CREATED'
  | 'FILE_STORED'
  | 'PREPARED'
  | 'ROLLBACK_REQUIRED'
  | 'SUCCESS'
  | 'FAILED'
type HistoryScope = 'mine' | 'all'

interface ImportHistory {
  id: string
  createdAt: string
  status: ImportStatus
  username: string
  moviesCount: number | null
  personsCount: number | null
  coordinatesCount: number | null
  locationsCount: number | null
  fileObjectKey: string | null
  fileName: string | null
  fileContentType: string | null
  fileSize: number | null
}

const auth = useAuthStore()
const { importHistoryVersion } = storeToRefs(useDatabaseStore())
const file = ref<File | null>(null)
const importing = ref(false)
const result = ref<ImportHistory | null>(null)
const history = ref<ImportHistory[]>([])
const historyScope = ref<HistoryScope>('mine')
const historyPage = ref(0)
const historyPages = ref(0)
const historyLoading = ref(false)
const isAdmin = computed(() => auth.role === 'ADMIN')

const statusLabels: Record<ImportStatus, string> = {
  CREATED: 'создано',
  FILE_STORED: 'файл сохранён',
  PREPARED: 'подготовлено',
  ROLLBACK_REQUIRED: 'требуется откат',
  SUCCESS: 'успешно',
  FAILED: 'ошибка',
}

function isFailureStatus(status: ImportStatus) {
  return status === 'FAILED' || status === 'ROLLBACK_REQUIRED'
}

function formatFileSize(bytes: number | null) {
  if (bytes === null) return 'размер неизвестен'
  if (bytes < 1024) return `${bytes} B`

  const kilobytes = bytes / 1024
  if (kilobytes < 1024) return `${kilobytes.toFixed(1)} KB`

  return `${(kilobytes / 1024).toFixed(1)} MB`
}

const dateFormatter = new Intl.DateTimeFormat('ru-RU', {
  dateStyle: 'short',
  timeStyle: 'short',
})

async function loadHistory() {
  try {
    historyLoading.value = true
    const path = historyScope.value === 'all' ? '/imports/history/all' : '/imports/history'
    const page = (await apiGet(`${path}?page=${historyPage.value}&size=8`)) as {
      content: ImportHistory[]
      totalPages: number
    }
    history.value = page.content
    historyPages.value = page.totalPages
  } catch (error) {
    showError(
      'не удалось загрузить историю :((',
      error instanceof Error ? error.message : 'нам не сообщили что за ошибка',
    )
  } finally {
    historyLoading.value = false
  }
}

function selectHistory(scope: HistoryScope) {
  historyScope.value = scope
  historyPage.value = 0
  void loadHistory()
}

function changeHistoryPage(offset: number) {
  historyPage.value += offset
  void loadHistory()
}

function selectFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0] ?? null
  result.value = null
}

async function importFile() {
  if (!file.value) return

  try {
    importing.value = true
    result.value = (await apiPostFile('/imports', file.value)) as ImportHistory
    historyPage.value = 0
    await loadHistory()
  } catch (error) {
    showError(
      'не удалось импортировать файл :((',
      error instanceof Error ? error.message : 'нам не сообщили что за ошибка',
    )
  } finally {
    importing.value = false
  }
}

async function downloadFile(item: ImportHistory) {
  try {
    const blob = await apiDownload(`/imports/history/${item.id}/file`)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')

    link.href = url
    link.download = item.fileName || 'import.hocon'
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch (error) {
    showError(
      'не удалось скачать файл :((',
      error instanceof Error ? error.message : 'нам не сообщили что за ошибка',
    )
  }
}

watch(importHistoryVersion, () => void loadHistory())
onMounted(loadHistory)
</script>

<template>
  <section class="import-view">
    <fieldset class="panel">
      <legend>IMPORT HISTORY</legend>

      <div class="history-tabs" role="tablist" aria-label="Import history scope">
        <button
          type="button"
          :class="{ active: historyScope === 'mine' }"
          @click="selectHistory('mine')"
        >
          моя история
        </button>
        <button
          v-if="isAdmin"
          type="button"
          :class="{ active: historyScope === 'all' }"
          @click="selectHistory('all')"
        >
          вся история
        </button>
      </div>

      <div class="history-list">
        <div v-if="historyLoading" class="empty-state">загрузка...</div>
        <div v-else-if="!history.length" class="empty-state">
          <strong>NO IMPORTS YET</strong>
          <span>операции импорта появятся здесь</span>
        </div>

        <article v-for="item in history" v-else :key="item.id" class="history-item">
          <div class="history-summary">
            <span class="status" :class="item.status.toLowerCase()">
              {{ statusLabels[item.status] }}
            </span>
            <time :datetime="item.createdAt">
              {{ dateFormatter.format(new Date(item.createdAt)) }}
            </time>
          </div>
          <div class="history-body">
            <div class="history-details">
              <strong>@{{ item.username }}</strong>
              <code>{{ item.id }}</code>

              <div v-if="item.status === 'SUCCESS'" class="history-counts">
                <span
                  ><b>{{ item.moviesCount }}</b> movies</span
                >
                <span
                  ><b>{{ item.personsCount }}</b> persons</span
                >
                <span
                  ><b>{{ item.coordinatesCount }}</b> coordinates</span
                >
                <span
                  ><b>{{ item.locationsCount }}</b> locations</span
                >
              </div>
              <small v-else-if="isFailureStatus(item.status)">объекты не были созданы</small>
              <small v-else>файл обрабатывается</small>

              <div v-if="item.fileName" class="history-file-info">
                <span>file: <strong>{{ item.fileName }}</strong></span>
                <span>| {{ formatFileSize(item.fileSize) }}</span>
                <span v-if="item.fileContentType">| {{ item.fileContentType }}</span>
              </div>

              <a
                v-if="item.fileObjectKey"
                class="history-download"
                href="#"
                @click.prevent="downloadFile(item)"
              >
                скачать файл онлайн
              </a>
            </div>
            <img
              class="history-gif"
              :src="isFailureStatus(item.status) ? danger : radioactive"
              alt=""
              aria-hidden="true"
            />
          </div>
        </article>
      </div>

      <div class="history-pagination">
        <button
          type="button"
          :disabled="historyPage === 0"
          @click="changeHistoryPage(-1)"
        >
          &larr; prev
        </button>
        <span>
          Page: <strong>{{ String(historyPage + 1).padStart(2, '0') }}</strong
          >/{{ String(Math.max(historyPages, 1)).padStart(2, '0') }}
        </span>
        <button
          type="button"
          :disabled="historyPage + 1 >= historyPages"
          @click="changeHistoryPage(1)"
        >
          next &rarr;
        </button>
      </div>
    </fieldset>

    <form class="import-panel-form" @submit.prevent="importFile">
      <fieldset class="panel">
        <legend>new HOCON (.conf) import</legend>

        <div class="file-form">
          <details class="format-help">
            <summary>выберите <strong>.conf</strong> или <strong>.hocon</strong> файл.</summary>
            <p>
              <a
                href="https://github.com/lightbend/config/blob/main/HOCON.md"
                target="_blank"
                rel="noopener noreferrer"
              >
                подробнее о формате hocon
              </a>
            </p>
          </details>

          <input type="file" accept=".conf,.hocon" @change="selectFile" />
          <button type="submit" :disabled="!file || importing">
            {{ importing ? 'importing...' : 'import file' }}
          </button>

          <div v-if="result" class="import-result" role="status">
            <strong>import successful!!</strong>
            <span>movies: {{ result.moviesCount }}</span>
            <span>persons: {{ result.personsCount }}</span>
            <span>coordinates: {{ result.coordinatesCount }}</span>
            <span>locations: {{ result.locationsCount }}</span>
          </div>
        </div>
      </fieldset>
    </form>
  </section>
</template>

<style scoped>
.import-view {
  padding: 18px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  font: 16px/1.35 'Times New Roman', Times, serif;
}

.panel {
  min-width: 0;
  min-height: 480px;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  border: 2px groove #b9a98a;
  background: var(--paper);
}

.panel > legend {
  margin-left: 8px;
  padding: 0 6px;
  color: var(--ink);
  background: var(--paper);
  font-size: 14px;
  font-weight: 700;
}

.import-panel-form {
  min-width: 0;
  margin: 0;
}

.import-panel-form > .panel {
  width: 100%;
  height: 100%;
}

.history-tabs {
  padding: 8px 10px;
  border-bottom: 1px solid #a35237;
}

.history-tabs button {
  margin-right: 6px;
  padding: 5px 10px;
  border: 0;
  color: white;
  background: var(--brick);
  cursor: pointer;
}

.history-tabs button.active {
  font-weight: 800;
  background: var(--burgundy);
}

.history-list {
  height: 430px;
  padding: 6px 12px;
  overflow: auto;
}

.history-item {
  padding: 12px 2px;
  border-bottom: 1px dashed #805a49;
}

.history-item:last-child {
  border-bottom: 0;
}

.history-body {
  margin-top: 7px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 52px;
  align-items: start;
  gap: 8px;
}

.history-details {
  display: grid;
  gap: 5px;
}

.history-details > strong {
  color: var(--brick);
}

.history-gif {
  width: 52px;
  height: 52px;
  object-fit: contain;
}

.history-item code {
  overflow-wrap: anywhere;
  color: #805a49;
  font-size: 11px;
}

.history-summary {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.status {
  font-weight: 800;
}

.status.success {
  color: #27622f;
}

.status.failed,
.status.rollback_required {
  color: #8b2823;
}

.status.created,
.status.file_stored,
.status.prepared {
  color: #7b5716;
}

.history-counts {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 14px;
}

.history-counts b {
  color: var(--brick);
}

.history-file-info {
  padding: 4px 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 0 5px;
  border: 1px solid #a99782;
  color: inherit;
  background: var(--paper);
  font: inherit;
}

.history-pagination {
  margin-top: auto;
  padding: 9px;
  border-top: 1px solid var(--ink);
  text-align: center;
}

.history-pagination button {
  margin: 0 8px;
  padding: 0;
  border: 0;
  color: #0000ee;
  background: transparent;
  text-decoration: underline;
  cursor: pointer;
}

.history-pagination button:disabled {
  color: #777;
  text-decoration: none;
  cursor: default;
}

.empty-state {
  padding: 70px 20px;
  color: #805a49;
  text-align: center;
}

.empty-state > * {
  display: block;
  margin: 6px 0;
}

.file-form {
  min-height: 430px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  text-align: center;
}

.file-form p {
  margin: 8px 0 0;
}

.format-help summary {
  cursor: pointer;
}

.import-result {
  padding: 10px 14px;
  border: 2px groove #b77855;
  background: #f6d9a4;
  text-align: left;
}

.import-result strong,
.import-result span {
  display: block;
}

.import-result strong {
  margin-bottom: 5px;
  color: var(--brick);
}

@media (max-width: 760px) {
  .import-view {
    padding: 10px;
    grid-template-columns: 1fr;
  }

  .panel,
  .file-form {
    min-height: 180px;
  }
}
</style>

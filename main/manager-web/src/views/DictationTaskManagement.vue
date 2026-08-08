<template>
  <div class="welcome">
    <HeaderBar />
    <div class="main-wrapper">
      <div class="content-panel">
        <div class="content-area">
          <el-card class="params-card" shadow="never">
            <div class="operation-header">
              <h2 class="page-title">听写任务</h2>
              <div class="right-operations">
                <el-input
                  placeholder="按任务名称搜索"
                  v-model="searchKeyword"
                  class="search-input"
                  @keyup.enter="handleSearch"
                  clearable
                />
                <el-select
                  v-model="searchStatus"
                  placeholder="状态"
                  clearable
                  class="status-select"
                >
                  <el-option label="启用" :value="1" />
                  <el-option label="禁用" :value="0" />
                </el-select>
                <CustomButton icon="el-icon-search" type="confirm" size="small" @click="handleSearch">
                  搜索
                </CustomButton>
                <CustomButton type="add" icon="el-icon-plus" size="small" @click="handleAdd">
                  新建任务
                </CustomButton>
              </div>
            </div>
            <CustomTable
              ref="taskTable"
              :data="paramsList"
              :columns="tableColumns"
              :loading="loading"
              :show-operations="true"
              operations-label="操作"
              :total="total"
              :current-page="currentPage"
              :page-size="pageSize"
              :page-size-options="pageSizeOptions"
              @size-change="handlePageSizeChange"
              @page-change="goToPage"
            >
              <template #mode="scope">
                <el-tag size="small" :type="scope.row.mode === 'listen_en' ? 'primary' : 'success'">
                  {{ scope.row.mode === 'listen_en' ? '播报英文' : '播报中文' }}
                </el-tag>
              </template>
              <template #accent="scope">
                {{ scope.row.accent === 'uk' ? '英式' : '美式' }}
              </template>
              <template #status="scope">
                <el-tag size="small" :type="scope.row.status === 1 ? 'success' : 'info'">
                  {{ scope.row.status === 1 ? '启用' : '禁用' }}
                </el-tag>
              </template>
              <template #operations="scope">
                <el-button size="small" link @click="handleEdit(scope.row)">编辑</el-button>
                <el-button size="small" link @click="handleStatusToggle(scope.row)">
                  {{ scope.row.status === 1 ? '禁用' : '启用' }}
                </el-button>
                <el-button size="small" link type="danger" @click="handleDelete(scope.row)">删除</el-button>
              </template>
            </CustomTable>
          </el-card>
        </div>
      </div>
    </div>

    <el-dialog
      :title="dialogTitle"
      v-model="dialogVisible"
      width="900px"
      :close-on-click-modal="false"
      destroy-on-close
      @close="onDialogClose"
    >
      <el-form
        ref="taskForm"
        :model="form"
        :rules="rules"
        label-width="120px"
        label-position="right"
      >
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="form.taskName" placeholder="例如：三年级上册 Unit 1" maxlength="64" show-word-limit />
        </el-form-item>

        <el-form-item label="播报模式" prop="mode">
          <el-radio-group v-model="form.mode">
            <el-radio value="listen_en">播报英文单词</el-radio>
            <el-radio value="listen_cn">播报中文释义</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="口音" prop="accent">
          <el-radio-group v-model="form.accent">
            <el-radio value="us">美式</el-radio>
            <el-radio value="uk">英式</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="单词间隔(秒)" prop="intervalSeconds">
          <el-input-number v-model="form.intervalSeconds" :min="1" :max="60" :step="1" :precision="0" />
        </el-form-item>

        <el-form-item label="播报次数" prop="repeatCount">
          <el-input-number v-model="form.repeatCount" :min="1" :step="1" :precision="0" />
          <span class="form-hint">每个单词重复播报次数</span>
        </el-form-item>

        <el-form-item label="重复间隔(秒)" prop="repeatIntervalSeconds">
          <el-input-number v-model="form.repeatIntervalSeconds" :min="0" :max="30" :step="1" :precision="0" />
          <span class="form-hint">同一单词重复播报之间的间隔</span>
        </el-form-item>

        <el-form-item label="语速" prop="speakRate">
          <el-slider v-model="form.speakRate" :min="-100" :max="100" :step="10" style="width: 320px" />
          <span class="form-hint">-100 慢 / 0 标准 / 100 快（仅对单词播报生效）</span>
        </el-form-item>

        <el-divider content-position="left">单词列表</el-divider>

        <el-form-item label="单词列表">
          <div class="word-list-container">
            <div class="word-list-toolbar">
              <el-button size="small" type="warning" plain @click="openBatchImport">批量导入</el-button>
              <el-button size="small" type="success" plain @click="openBookPicker">从词书选择</el-button>
              <span class="word-count">共 {{ validWordCount }} 词</span>
            </div>
            <el-table :data="unifiedWords" border size="small" max-height="320" style="margin-top: 8px">
              <el-table-column label="序号" type="index" width="60" align="center" />
              <el-table-column label="英文单词" min-width="160">
                <template #default="scope">
                  <el-input v-model="scope.row.word" placeholder="apple" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="中文释义" min-width="200">
                <template #default="scope">
                  <el-input v-model="scope.row.meaning" placeholder="苹果" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="来源" width="90" align="center">
                <template #default="scope">
                  <el-tag size="small" :type="scope.row.source === 'book' ? 'success' : 'info'">
                    {{ scope.row.source === 'book' ? '词书' : '手动' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center">
                <template #default="scope">
                  <el-button size="small" link type="danger" @click="removeUnifiedWord(scope.$index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-form-item>

        <el-divider content-position="left">状态</el-divider>

        <el-form-item label="启用状态">
          <el-switch v-model="form.statusBool" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>

      <!-- 词书选择对话框 -->
      <el-dialog
        v-model="bookPickerVisible"
        title="从词书选择单词"
        width="700px"
        append-to-body
        :close-on-click-modal="false"
        destroy-on-close
      >
        <div class="book-picker">
          <div class="book-picker-toolbar">
            <el-select
              v-model="pickerBookId"
              placeholder="请选择词书"
              filterable
              style="width: 260px"
              @change="onPickerBookChange"
            >
              <el-option
                v-for="book in books"
                :key="book.id"
                :label="`${book.name}（共 ${book.totalWords} 词）`"
                :value="book.id"
              />
            </el-select>
            <el-input
              v-model="pickerKeyword"
              placeholder="搜索单词"
              style="width: 180px"
              clearable
              @keyup.enter="searchPickerWords"
            />
            <el-button size="small" @click="searchPickerWords">搜索</el-button>
            <span class="picker-hint">已自动过滤已标熟的单词</span>
          </div>
          <el-table
            :data="pickerWords"
            v-loading="pickerLoading"
            border
            size="small"
            max-height="360"
            @selection-change="onPickerSelectionChange"
            :row-key="row => row.id"
            ref="pickerTable"
          >
            <el-table-column type="selection" width="50" align="center" />
            <el-table-column prop="word" label="单词" min-width="120" />
            <el-table-column prop="meaning" label="中文释义" min-width="180" show-overflow-tooltip />
            <el-table-column prop="phoneticUs" label="美式音标" min-width="120" />
            <el-table-column label="操作" width="80" align="center">
              <template #default="scope">
                <el-button
                  size="small"
                  link
                  type="primary"
                  :loading="pickerMarkingId === scope.row.id"
                  @click="handlePickerMarkFamiliar(scope.row)"
                >标熟</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="book-picker-pagination">
            <el-pagination
              background
              layout="total, prev, pager, next"
              :total="pickerTotal"
              :current-page="pickerCurrentPage"
              :page-size="pickerPageSize"
              @current-change="onPickerPageChange"
            />
          </div>
        </div>
        <template #footer>
          <el-button @click="bookPickerVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmBookPicker">
            确认添加（已选 {{ pickerSelected.length }} 词）
          </el-button>
        </template>
      </el-dialog>

      <!-- 批量导入对话框 -->
      <el-dialog
        v-model="batchImportVisible"
        title="批量导入单词"
        width="600px"
        append-to-body
        :close-on-click-modal="false"
        destroy-on-close
      >
        <el-form label-width="100px">
          <el-form-item label="单词列表">
            <el-input
              v-model="batchImportText"
              type="textarea"
              :rows="12"
              placeholder="每行一个英文单词，例如：&#10;apple&#10;banana&#10;orange"
            />
            <span class="form-hint">自动从词书查找释义，找不到的用大模型翻译；已标熟的单词会被自动过滤</span>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="batchImportVisible = false">取消</el-button>
          <el-button type="primary" :loading="batchImporting" @click="confirmBatchImport">
            导入（{{ batchImportWordCount }} 词）
          </el-button>
        </template>
      </el-dialog>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
    <el-footer><VersionFooter /></el-footer>
  </div>
</template>

<script>
import Api from "@/apis/api";
import HeaderBar from "@/components/HeaderBar.vue";
import VersionFooter from "@/components/VersionFooter.vue";
import CustomButton from "@/components/CustomButton.vue";
import CustomTable from "@/components/CustomTable.vue";

export default {
  name: "DictationTaskManagement",
  components: { HeaderBar, VersionFooter, CustomButton, CustomTable },
  data() {
    return {
      searchKeyword: "",
      searchStatus: "",
      paramsList: [],
      currentPage: 1,
      pageSize: 10,
      pageSizeOptions: [10, 20, 50, 100],
      total: 0,
      loading: false,

      dialogVisible: false,
      dialogTitle: "新建听写任务",
      saving: false,
      form: this.buildEmptyForm(),
      rules: {
        taskName: [{ required: true, message: "请输入任务名称", trigger: "blur" }],
        mode: [{ required: true, message: "请选择播报模式", trigger: "change" }],
        accent: [{ required: true, message: "请选择口音", trigger: "change" }]
      },

      books: [],
      unifiedWords: [],

      // 词书选择对话框
      bookPickerVisible: false,
      pickerBookId: undefined,
      pickerKeyword: "",
      pickerWords: [],
      pickerLoading: false,
      pickerTotal: 0,
      pickerCurrentPage: 1,
      pickerPageSize: 20,
      pickerSelected: [],
      pickerMarkingId: null,

      // 批量导入
      batchImportVisible: false,
      batchImportText: "",
      batchImporting: false,

      tableColumns: []
    };
  },
  computed: {
    validWordCount() {
      return this.unifiedWords.filter(w => {
        if (w.source === 'book') return w.vocabId;
        return (w.word || "").trim() && (w.meaning || "").trim();
      }).length;
    },
    batchImportWordCount() {
      return this.batchImportText.split('\n').map(w => w.trim()).filter(w => w).length;
    }
  },
  created() {
    this.initTableColumns();
    this.fetchTaskList();
    this.fetchBooks();
  },
  methods: {
    buildEmptyForm() {
      return {
        id: undefined,
        taskName: "",
        mode: "listen_en",
        accent: "us",
        intervalSeconds: 5,
        repeatCount: 1,
        repeatIntervalSeconds: 1,
        speakRate: 0,
        statusBool: true
      };
    },

    initTableColumns() {
      this.tableColumns = [
        { prop: "taskName", label: "任务名称", align: "center", minWidth: 160 },
        { prop: "mode", label: "播报模式", align: "center", slot: "mode" },
        { prop: "accent", label: "口音", align: "center", slot: "accent" },
        { prop: "wordCount", label: "单词数", align: "center" },
        { prop: "status", label: "状态", align: "center", slot: "status" },
        { prop: "createDate", label: "创建时间", align: "center", minWidth: 160 }
      ];
    },

    fetchTaskList() {
      this.loading = true;
      Api.dictation.getTaskPage({
        page: this.currentPage,
        limit: this.pageSize,
        taskName: this.searchKeyword,
        status: this.searchStatus
      }, ({ data }) => {
        this.loading = false;
        if (data.code === 0) {
          this.paramsList = data.data.list || [];
          this.total = data.data.total || 0;
        } else {
          this.$message.error(data.msg || "获取听写任务列表失败");
        }
      });
    },

    fetchBooks() {
      Api.dictation.listBooks(({ data }) => {
        if (data.code === 0) {
          this.books = data.data || [];
        }
      });
    },

    fetchPickerWords() {
      if (!this.pickerBookId) {
        this.pickerWords = [];
        this.pickerTotal = 0;
        return;
      }
      this.pickerLoading = true;
      Api.dictation.pageWordsByBook({
        bookId: this.pickerBookId,
        word: this.pickerKeyword,
        page: this.pickerCurrentPage,
        limit: this.pickerPageSize,
        excludeFamiliar: true
      }, ({ data }) => {
        this.pickerLoading = false;
        if (data.code === 0) {
          this.pickerWords = data.data.list || [];
          this.pickerTotal = data.data.total || 0;
          this.$nextTick(() => {
            this.restorePickerSelection();
          });
        } else {
          this.$message.error(data.msg || "获取词书单词失败");
        }
      });
    },

    restorePickerSelection() {
      if (!this.$refs.pickerTable) return;
      const existingIds = new Set(this.unifiedWords.filter(w => w.source === 'book').map(w => w.vocabId));
      this.pickerWords.forEach(row => {
        if (row.id && existingIds.has(row.id)) {
          this.$refs.pickerTable.toggleRowSelection(row, true);
        }
      });
    },

    openBookPicker() {
      this.pickerBookId = undefined;
      this.pickerKeyword = "";
      this.pickerWords = [];
      this.pickerTotal = 0;
      this.pickerCurrentPage = 1;
      this.pickerSelected = [];
      this.bookPickerVisible = true;
    },

    onPickerBookChange() {
      this.pickerCurrentPage = 1;
      this.pickerKeyword = "";
      this.fetchPickerWords();
    },

    searchPickerWords() {
      this.pickerCurrentPage = 1;
      this.fetchPickerWords();
    },

    onPickerPageChange(page) {
      this.pickerCurrentPage = page;
      this.fetchPickerWords();
    },

    onPickerSelectionChange(selection) {
      this.pickerSelected = selection;
    },

    handlePickerMarkFamiliar(row) {
      this.pickerMarkingId = row.id;
      Api.dictation.markFamiliar({
        vocabId: row.id,
        word: row.word,
        bookId: this.pickerBookId
      }, ({ data }) => {
        this.pickerMarkingId = null;
        if (data.code === 0) {
          this.$message.success(`${row.word} 已标熟`);
          // 标熟后重新加载列表（已标熟单词会被自动过滤）
          this.fetchPickerWords();
        } else {
          this.$message.error(data.msg || "标熟失败");
        }
      });
    },

    confirmBookPicker() {
      const existingIds = new Set(this.unifiedWords.filter(w => w.source === 'book').map(w => w.vocabId));
      let added = 0;
      this.pickerSelected.forEach(w => {
        if (w.id && !existingIds.has(w.id)) {
          this.unifiedWords.push({
            word: w.word || "",
            meaning: w.meaning || "",
            source: 'book',
            vocabId: w.id
          });
          added++;
        }
      });
      // 移除用户取消选中的词书单词
      const selectedIds = new Set(this.pickerSelected.map(w => w.id));
      this.unifiedWords = this.unifiedWords.filter(w => {
        if (w.source !== 'book') return true;
        // 只移除当前词书中的单词（在 pickerWords 范围内的）
        const inPicker = this.pickerWords.some(pw => pw.id === w.vocabId);
        if (inPicker && !selectedIds.has(w.vocabId)) return false;
        return true;
      });
      if (added > 0) {
        this.$message.success(`已添加 ${added} 个单词`);
      }
      this.bookPickerVisible = false;
    },

    removeUnifiedWord(index) {
      this.unifiedWords.splice(index, 1);
    },

    handleSearch() {
      this.currentPage = 1;
      this.fetchTaskList();
    },

    handlePageSizeChange(val) {
      this.pageSize = val;
      this.currentPage = 1;
      this.fetchTaskList();
    },

    goToPage(page) {
      if (page !== this.currentPage) {
        this.currentPage = page;
        this.fetchTaskList();
      }
    },

    handleAdd() {
      this.dialogTitle = "新建听写任务";
      this.unifiedWords = [];
      this.dialogVisible = true;
      // 先用空默认值，再尝试用当前启用任务的配置覆盖
      this.form = this.buildEmptyForm();
      Api.dictation.getActiveConfig(({ data }) => {
        if (data.code === 0 && data.data) {
          const active = data.data;
          this.form = {
            id: undefined,
            taskName: "",
            mode: active.mode || "listen_en",
            accent: active.accent || "us",
            intervalSeconds: active.intervalSeconds != null ? active.intervalSeconds : 5,
            repeatCount: active.repeatCount != null ? active.repeatCount : 1,
            repeatIntervalSeconds: active.repeatIntervalSeconds != null ? active.repeatIntervalSeconds : 1,
            speakRate: active.speakRate != null ? active.speakRate : 0,
            statusBool: true
          };
        }
      });
    },

    handleEdit(row) {
      this.dialogTitle = "编辑听写任务";
      Api.dictation.getTaskDetail(row.id, ({ data }) => {
        if (data.code !== 0) {
          this.$message.error(data.msg || "获取任务详情失败");
          return;
        }
        const detail = data.data;
        this.form = {
          id: detail.id,
          taskName: detail.taskName || "",
          mode: detail.mode || "listen_en",
          accent: detail.accent || "us",
          intervalSeconds: detail.intervalSeconds != null ? Number(detail.intervalSeconds) : 5,
          repeatCount: detail.repeatCount != null ? detail.repeatCount : 1,
          repeatIntervalSeconds: detail.repeatIntervalSeconds != null ? Number(detail.repeatIntervalSeconds) : 1,
          speakRate: detail.speakRate || 0,
          statusBool: detail.status === 1
        };

        // 统一加载单词列表，有 id 的是词书单词
        this.unifiedWords = (detail.words || []).map(w => ({
          word: w.word || "",
          meaning: w.meaning || "",
          source: w.source || (w.id ? 'book' : 'manual'),
          vocabId: w.id ? Number(w.id) : undefined
        }));

        this.dialogVisible = true;
      });
    },

    handleDelete(row) {
      this.$confirm(`确认删除任务「${row.taskName}」？`, "删除确认", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }).then(() => {
        Api.dictation.deleteTask(row.id, ({ data }) => {
          if (data.code === 0) {
            this.$message.success("删除成功");
            this.fetchTaskList();
          } else {
            this.$message.error(data.msg || "删除失败");
          }
        });
      }).catch(() => {});
    },

    handleStatusToggle(row) {
      const next = row.status === 1 ? 0 : 1;
      Api.dictation.updateTaskStatus(row.id, next, ({ data }) => {
        if (data.code === 0) {
          this.$message.success(next === 1 ? "已启用" : "已禁用");
          this.fetchTaskList();
        } else {
          this.$message.error(data.msg || "状态更新失败");
        }
      });
    },

    handleSubmit() {
      this.$refs.taskForm.validate(valid => {
        if (!valid) return;

        const validWords = this.unifiedWords.filter(w => {
          if (w.source === 'book') return w.vocabId;
          return (w.word || "").trim() && (w.meaning || "").trim();
        });

        if (validWords.length === 0) {
          this.$message.warning("请至少添加一个单词");
          return;
        }

        const words = validWords.map(w => {
          const item = { word: w.word.trim(), meaning: w.meaning.trim(), source: w.source || 'manual' };
          if (w.source === 'book' && w.vocabId) {
            item.id = w.vocabId;
          }
          return item;
        });

        const payload = {
          id: this.form.id,
          taskName: this.form.taskName,
          mode: this.form.mode,
          accent: this.form.accent,
          intervalSeconds: this.form.intervalSeconds,
          repeatCount: this.form.repeatCount,
          repeatIntervalSeconds: this.form.repeatIntervalSeconds,
          speakRate: this.form.speakRate,
          status: this.form.statusBool ? 1 : 0,
          words: words
        };

        this.saving = true;
        Api.dictation.saveTask(payload, ({ data }) => {
          this.saving = false;
          if (data.code === 0) {
            this.$message.success(this.form.id ? "保存成功" : "创建成功");
            this.dialogVisible = false;
            this.fetchTaskList();
          } else {
            this.$message.error(data.msg || "保存失败");
          }
        });
      });
    },

    openBatchImport() {
      this.batchImportText = "";
      this.batchImportVisible = true;
    },

    confirmBatchImport() {
      const words = this.batchImportText.split('\n').map(w => w.trim()).filter(w => w);
      if (words.length === 0) {
        this.$message.warning("请输入至少一个单词");
        return;
      }

      this.batchImporting = true;
      Api.dictation.batchImportWords({
        words: words
      }, ({ data }) => {
        this.batchImporting = false;
        if (data.code === 0) {
          const result = data.data || [];
          let added = 0;
          const existingWords = new Set(this.unifiedWords.map(w => (w.word || "").toLowerCase()));
          result.forEach(item => {
            if (!existingWords.has((item.word || "").toLowerCase())) {
              this.unifiedWords.push({
                word: item.word || "",
                meaning: item.meaning || "",
                source: 'manual'
              });
              added++;
            }
          });
          const skipped = words.length - result.length;
          let msg = `成功导入 ${added} 个单词`;
          if (skipped > 0) {
            msg += `，已过滤 ${skipped} 个已标熟单词`;
          }
          this.$message.success(msg);
          this.batchImportVisible = false;
        } else {
          this.$message.error(data.msg || "批量导入失败");
        }
      });
    },

    onDialogClose() {
      this.saving = false;
      if (this.$refs.taskForm) {
        this.$refs.taskForm.clearValidate();
      }
    }
  }
};
</script>

<style lang="scss" scoped>
.welcome {
  min-width: 900px;
  min-height: 506px;
  height: 100vh;
  display: flex;
  position: relative;
  flex-direction: column;
  background: #eff4ff;
  overflow: hidden;
}

.main-wrapper {
  height: calc(100vh - 63px - 35px);
  padding: 20px 22px 0;
  position: relative;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.operation-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 16px 0;
  flex-wrap: wrap;
  gap: 10px;
}

.page-title {
  font-weight: 500;
  font-size: 24px;
  margin: 0;
}

.right-operations {
  display: flex;
  gap: 10px;
  margin-left: auto;
  align-items: center;
}

.search-input {
  width: 200px;
}

.status-select {
  width: 120px;
}

.content-panel {
  display: flex;
  overflow: hidden;
  height: 100%;
  border-radius: 15px;
  background: transparent;
  border: 1px solid #fff;
}

.content-area {
  flex: 1;
  height: 100%;
  min-width: 600px;
  overflow: auto;
  background-color: white;
  display: flex;
  flex-direction: column;
}

.params-card {
  background: white;
  flex: 1;
  display: flex;
  flex-direction: column;
  border: none;
  box-shadow: none;
  overflow: hidden;

  :deep(.el-card__body) {
    padding: 14px 20px;
    display: flex;
    flex-direction: column;
    flex: 1;
    overflow: hidden;
  }
}

.form-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}

.word-list-container {
  width: 100%;
}

.word-list-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.word-count {
  margin-left: auto;
  color: #5778ff;
  font-size: 13px;
}

.book-picker {
  width: 100%;
}

.book-picker-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.picker-hint {
  color: #909399;
  font-size: 12px;
}

.book-picker-pagination {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}
</style>

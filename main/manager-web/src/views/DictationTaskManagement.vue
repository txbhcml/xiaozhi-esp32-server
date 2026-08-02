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
              <template #introduceWords="scope">
                <el-tag size="small" :type="scope.row.introduceWords ? 'success' : 'info'">
                  {{ scope.row.introduceWords ? '是' : '否' }}
                </el-tag>
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
          <el-input-number v-model="form.repeatCount" :min="1" :max="3" :step="1" :precision="0" />
          <span class="form-hint">每个单词重复播报 1~3 次</span>
        </el-form-item>

        <el-form-item label="语速" prop="speakRate">
          <el-slider v-model="form.speakRate" :min="-100" :max="100" :step="10" style="width: 320px" />
          <span class="form-hint">-100 慢 / 0 标准 / 100 快</span>
        </el-form-item>

        <el-divider content-position="left">单词介绍阶段</el-divider>

        <el-form-item label="介绍所有单词">
          <el-switch v-model="form.introduceWords" />
          <span class="form-hint">听写开始前，先介绍所有单词（含拼写、例句等）</span>
        </el-form-item>

        <el-form-item label="播报例句" v-if="form.introduceWords">
          <el-switch v-model="form.showExample" />
        </el-form-item>

        <el-form-item label="翻译例句" v-if="form.introduceWords && form.showExample">
          <el-switch v-model="form.exampleTranslate" />
        </el-form-item>

        <el-form-item label="提示近反义词" v-if="form.introduceWords">
          <el-switch v-model="form.showSynonym" />
        </el-form-item>

        <el-divider content-position="left">单词列表</el-divider>

        <el-form-item label="单词列表">
          <div class="word-list-container">
            <div class="word-list-toolbar">
              <el-button size="small" type="primary" plain @click="addManualWord">+ 添加单词</el-button>
              <el-button size="small" type="success" plain @click="openBookPicker">从词书选择</el-button>
              <span class="word-count">共 {{ validWordCount }} 词</span>
            </div>
            <el-table :data="unifiedWords" border size="small" max-height="320" style="margin-top: 8px">
              <el-table-column label="序号" type="index" width="60" align="center" />
              <el-table-column label="英文单词" min-width="160">
                <template #default="scope">
                  <el-input v-model="scope.row.word" placeholder="apple" size="small" :disabled="scope.row.source === 'book'" />
                </template>
              </el-table-column>
              <el-table-column label="中文释义" min-width="200">
                <template #default="scope">
                  <el-input v-model="scope.row.meaning" placeholder="苹果" size="small" :disabled="scope.row.source === 'book'" />
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

      tableColumns: []
    };
  },
  computed: {
    validWordCount() {
      return this.unifiedWords.filter(w => {
        if (w.source === 'book') return w.vocabId;
        return (w.word || "").trim() && (w.meaning || "").trim();
      }).length;
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
        speakRate: 0,
        introduceWords: false,
        showExample: false,
        exampleTranslate: false,
        showSynonym: false,
        statusBool: true
      };
    },

    initTableColumns() {
      this.tableColumns = [
        { prop: "taskName", label: "任务名称", align: "center", minWidth: 160 },
        { prop: "mode", label: "播报模式", align: "center", slot: "mode" },
        { prop: "accent", label: "口音", align: "center", slot: "accent" },
        { prop: "wordCount", label: "单词数", align: "center" },
        { prop: "introduceWords", label: "介绍单词", align: "center", slot: "introduceWords" },
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
        limit: this.pickerPageSize
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

    addManualWord() {
      this.unifiedWords.push({ word: "", meaning: "", source: 'manual' });
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
      this.form = this.buildEmptyForm();
      this.unifiedWords = [];
      this.dialogTitle = "新建听写任务";
      this.dialogVisible = true;
    },

    handleEdit(row) {
      this.dialogTitle = "编辑听写任务";
      Api.dictation.getTaskDetail(row.id, ({ data }) => {
        if (data.code !== 0) {
          this.$message.error(data.msg || "获取任务详情失败");
          return;
        }
        const detail = data.data;
        const bookIdSet = new Set((detail.selectedWordIds || []).map(id => Number(id)));
        this.form = {
          id: detail.id,
          taskName: detail.taskName || "",
          mode: detail.mode || "listen_en",
          accent: detail.accent || "us",
          intervalSeconds: detail.intervalSeconds != null ? Number(detail.intervalSeconds) : 5,
          repeatCount: detail.repeatCount != null ? detail.repeatCount : 1,
          speakRate: detail.speakRate || 0,
          introduceWords: !!detail.introduceWords,
          showExample: !!detail.showExample,
          exampleTranslate: !!detail.exampleTranslate,
          showSynonym: !!detail.showSynonym,
          statusBool: detail.status === 1
        };

        // 统一加载单词列表，区分来源
        this.unifiedWords = (detail.words || []).map(w => {
          const isBook = w.id && bookIdSet.has(Number(w.id));
          return {
            word: w.word || "",
            meaning: w.meaning || "",
            source: isBook ? 'book' : 'manual',
            vocabId: isBook ? Number(w.id) : undefined
          };
        });

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

        const bookWords = this.unifiedWords.filter(w => w.source === 'book' && w.vocabId);
        const manualWords = this.unifiedWords
          .filter(w => w.source === 'manual' && (w.word || "").trim() && (w.meaning || "").trim())
          .map(w => ({ word: w.word.trim(), meaning: w.meaning.trim() }));

        if (bookWords.length === 0 && manualWords.length === 0) {
          this.$message.warning("请至少添加一个单词");
          return;
        }

        const payload = {
          id: this.form.id,
          taskName: this.form.taskName,
          mode: this.form.mode,
          accent: this.form.accent,
          intervalSeconds: this.form.intervalSeconds,
          repeatCount: this.form.repeatCount,
          speakRate: this.form.speakRate,
          introduceWords: this.form.introduceWords,
          showExample: this.form.showExample,
          exampleTranslate: this.form.exampleTranslate,
          showSynonym: this.form.showSynonym,
          status: this.form.statusBool ? 1 : 0,
          bookId: null,
          selectedWordIds: bookWords.map(w => w.vocabId),
          words: manualWords
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

.book-picker-pagination {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}
</style>

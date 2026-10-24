Component({
  properties: {
    state: String,
    message: String,
    emptyText: { type: String, value: "暂时还没有内容" },
  },
  methods: {
    retry() {
      this.triggerEvent("retry");
    },
    login() {
      this.triggerEvent("login");
    },
  },
});

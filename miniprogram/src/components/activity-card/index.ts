Component({
  properties: { activity: { type: Object, value: { id: 0 } } },
  methods: {
    open() {
      this.triggerEvent("open", { id: this.data.activity.id });
    },
  },
});

import matplotlib.pyplot as plt

experiments = ['Exp1', 'Exp2', 'Exp3', 'Exp4', 'Exp5', 'Exp6', 'Exp7']
p99_latency = [20, 21, 19, 50, 98, 17, 16]

plt.figure(figsize=(10, 6))
plt.plot(experiments, p99_latency, marker='o', linewidth=2, markersize=8)
plt.xlabel('Experiment', fontsize=12)
plt.ylabel('P99 Latency (ms)', fontsize=12)
plt.title('P99 Latency Across Experiments', fontsize=14, fontweight='bold')
plt.grid(True, alpha=0.3)

for i, (exp, lat) in enumerate(zip(experiments, p99_latency)):
    plt.annotate(f'{lat}ms', (i, lat), textcoords="offset points", xytext=(0,10), ha='center')

plt.tight_layout()
plt.savefig('/Users/balasvij/opensearch/performance_graph.png', dpi=300)
print("Graph saved to: /Users/balasvij/opensearch/performance_graph.png")

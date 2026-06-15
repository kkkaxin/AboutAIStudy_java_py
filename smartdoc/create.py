import chromadb

print("=" * 50)
print("创建 Chroma Collection (SpringAiTenant / SpringAiDatabase)")
print("=" * 50)

# 用 chromadb 0.5+ 的新写法指定 tenant 和 database
client = chromadb.HttpClient(
    host='localhost',
    port=8000,
    tenant='SpringAiTenant',
    database='SpringAiDatabase'
)

print(f"\n当前所有 collections: {[c.name for c in client.list_collections()]}")

# 显式创建
collection = client.get_or_create_collection(name='smartdoc_vectors')
print(f"\n✅ 创建成功：{collection.name}")
print(f"✅ 完整路径：tenants/SpringAiTenant/databases/SpringAiDatabase/collections/smartdoc_vectors")

print(f"\n更新后所有 collections: {[c.name for c in client.list_collections()]}")

export function createUserHandlers(apiRequest) {
  return {
    loginAuthentication: async () => apiRequest("/users"),
    listUsers: async () => apiRequest("/users"),
    createUser: async ({ values }) => apiRequest("/users", {
      method: "POST",
      body: {
        username: values.username,
        email: values.email,
        password: values.password,
        role: values.role,
        merchantId: values.merchantId || null,
        active: values.active !== "false",
      },
    }),
    changeRole: async ({ values }) => apiRequest(`/users/${values.username}`, {
      method: "PUT",
      body: {
        password: values.password || undefined,
        role: values.role || undefined,
      },
    }),
    updateUser: async ({ values }) => apiRequest(`/users/${values.username}`, {
      method: "PUT",
      body: {
        email: values.email || undefined,
        password: values.password || undefined,
        role: values.role || undefined,
        merchantId: values.merchantId || undefined,
        active: values.active ? values.active !== "false" : undefined,
      },
    }),
    deleteUser: async ({ values }) => apiRequest(`/users/${values.username}`, { method: "DELETE" }),
  };
}

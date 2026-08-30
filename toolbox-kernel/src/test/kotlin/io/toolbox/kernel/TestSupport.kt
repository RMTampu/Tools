package io.toolbox.kernel

internal fun module(
    id: String,
    version: String = "1.0.0",
    dependencies: Set<ModuleDependency> = emptySet(),
    providedCapabilities: Set<CapabilityDeclaration> = emptySet(),
    requiredCapabilities: Set<CapabilityRequirement> = emptySet(),
    onLoadBlock: (KernelContext) -> Unit = {},
    onStartBlock: () -> Unit = {},
    onStopBlock: () -> Unit = {},
    onUnloadBlock: () -> Unit = {},
    healthBlock: () -> HealthStatus = { HealthStatus.ok() }
): ToolBoxModule = object : ToolBoxModule {
    override val descriptor: ModuleDescriptor = ModuleDescriptor(
        id = id,
        name = id,
        version = version,
        dependencies = dependencies,
        providedCapabilities = providedCapabilities,
        requiredCapabilities = requiredCapabilities
    )

    override fun onLoad(context: KernelContext): Unit = onLoadBlock(context)
    override fun onStart(): Unit = onStartBlock()
    override fun onStop(): Unit = onStopBlock()
    override fun onUnload(): Unit = onUnloadBlock()
    override fun healthCheck(): HealthStatus = healthBlock()
}

internal fun capability(id: String, version: String, provider: String): Capability = object : Capability {
    override val id: String = id
    override val version: ModuleVersion = ModuleVersion.parse(version)
    override val providerModuleId: String = provider
}

internal fun command(name: String): KernelCommand = object : KernelCommand {
    override val name: String = name
}

internal fun staged(source: ModuleSource): StagedModuleSource = StagedModuleSource(
    sourceId = source.id,
    artifactId = "artifact:${source.id}",
    location = "internal:${source.location}",
    metadata = source.metadata,
    immutable = true
)

internal fun authoritativePorts(
    logger: KernelLogger = NoopKernelLogger,
    clock: KernelClock = SystemKernelClock,
    executor: KernelExecutor = DirectKernelExecutor,
    stager: ModuleSourceStager = ModuleSourceStager(::staged),
    verifier: ModuleSourceVerifier = ModuleSourceVerifier { _, _ ->
        SourceVerificationResult(
            verified = true,
            fingerprint = "sha256:test",
            algorithm = "SHA-256",
            signerId = "test-signer",
            policyId = "test-policy"
        )
    },
    admissionPolicy: ModuleAdmissionPolicy = AllowAllModuleAdmissionPolicy
): KernelPorts = KernelPorts(
    logger = logger,
    clock = clock,
    executor = executor,
    runtimeEnvironment = KernelRuntimeEnvironment.authoritative(30, "arm64-v8a"),
    admissionPolicy = admissionPolicy,
    sourceStager = stager,
    sourceVerifier = verifier
)

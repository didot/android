load("//tools/base/bazel:merge_archives.bzl", "run_singlejar")
load("//tools/base/bazel:functions.bzl", "create_option_file")
load("@bazel_tools//tools/jdk:toolchain_utils.bzl", "find_java_toolchain")

def _zipper(ctx, desc, map, out, deps = []):
    files = [f for (p, f) in map]
    zipper_files = [r + "=" + f.path + "\n" for r, f in map]
    zipper_args = ["cC" if ctx.attr.compress else "c", out.path]
    zipper_list = create_option_file(ctx, out.basename + ".res.lst", "".join(zipper_files))
    zipper_args += ["@" + zipper_list.path]
    ctx.actions.run(
        inputs = files + [zipper_list] + deps,
        outputs = [out],
        executable = ctx.executable._zipper,
        arguments = zipper_args,
        progress_message = "Creating %s zip..." % desc,
        mnemonic = "zipper",
    )

# Bazel does not support attributes of type 'dict of string -> list of labels',
# and in order to support them we must 'unpack' the dictionary to two lists
# of keys and value. The following two functions perform the mapping back and forth
def _dict_to_lists(dict):
    keys = []
    values = []
    for k, vs in dict.items():
        keys += [k] * len(vs)
        values += vs
    return keys, values

def _lists_to_dict(keys, values):
    dict = {}
    for k, v in zip(keys, values):
        if k not in dict:
            dict[k] = []
        dict[k] += [v]
    return dict

def _module_deps(ctx, jar_names, modules):
    jars = _lists_to_dict(jar_names, modules)
    bundled = {}
    res_files = []
    plugin_jar = None
    plugin_xml = None
    for j, ms in jars.items():
        jar_file = ctx.actions.declare_file(j)
        modules_jars = []
        for m in ms:
            if not hasattr(m, "module"):
                fail("Only iml_modules are allowed in modules")
            if m.module.plugin:
                plugin_jar = j
                plugin_xml = m.module.plugin
            modules_jars += [m.module.module_jars]
            for dep in m.module.bundled_deps:
                if dep in bundled:
                    continue
                res_files += [(dep.basename, dep)]
                bundled[dep] = True
        run_singlejar(ctx, modules_jars, jar_file)
        res_files += [(j, jar_file)]
    return res_files, plugin_jar, plugin_xml

def _get_linux(dep):
    return dep.files.to_list() + dep.files_linux.to_list()

LINUX = struct(
    name = "linux",
    jre = "jre/",
    get = _get_linux,
    base_path = "",
    resource_path = "",
)

def _get_mac(dep):
    return dep.files.to_list() + dep.files_mac.to_list()

MAC = struct(
    name = "mac",
    jre = "jre/jdk/",
    get = _get_mac,
    base_path = "Contents/",
    resource_path = "Contents/Resources/",
)

def _get_win(dep):
    return dep.files.to_list() + dep.files_win.to_list()

WIN = struct(
    name = "win",
    jre = "jre/",
    get = _get_win,
    base_path = "",
    resource_path = "",
)

def _resource_deps(res_dirs, res, platform):
    files = []
    for dir, dep in zip(res_dirs, res):
        if hasattr(dep, "mappings"):
            files += [(dir + "/" + dep.mappings[f], f) for f in platform.get(dep)]
        else:
            files += [(dir + "/" + f.basename, f) for f in dep.files.to_list()]
    return files

def _check_plugin(ctx, files, verify_id = None, verify_deps = None):
    deps = None
    if verify_deps != None:
        deps = [dep.plugin_info for dep in verify_deps if hasattr(dep, "plugin_info")]

    plugin_info = ctx.actions.declare_file(ctx.attr.name + ".info")
    check_args = ctx.actions.args()
    check_args.add("--out", plugin_info)
    check_args.add_all("--files", files)
    if verify_id:
        check_args.add("--plugin_id", verify_id)
    if deps != None:
        check_args.add_all("--deps", deps, omit_if_empty = False)

    ctx.actions.run(
        inputs = files + (deps if deps else []),
        outputs = [plugin_info],
        executable = ctx.executable._check_plugin,
        arguments = [check_args],
        progress_message = "Analyzing %s plugin..." % ctx.attr.name,
        mnemonic = "chkplugin",
    )
    return plugin_info

def _studio_plugin_os(ctx, platform, module_deps, plugin_dir, plugin_info, out):
    spec = [(plugin_dir + "/lib/" + d, f) for (d, f) in module_deps]

    res = _resource_deps(ctx.attr.resources_dirs, ctx.attr.resources, platform)
    spec += [(plugin_dir + "/" + d, f) for (d, f) in res]

    files = [f for (p, f) in spec]
    _zipper(ctx, "%s plugin" % platform.name, spec, out, [plugin_info])

def _studio_plugin_impl(ctx):
    plugin_dir = "plugins/" + ctx.attr.directory
    module_deps, plugin_jar, plugin_xml = _module_deps(ctx, ctx.attr.jars, ctx.attr.modules)
    plugin_info = _check_plugin(ctx, [f for (r, f) in module_deps], ctx.attr.name, ctx.attr.deps)
    _studio_plugin_os(ctx, LINUX, module_deps, plugin_dir, plugin_info, ctx.outputs.plugin_linux)
    _studio_plugin_os(ctx, MAC, module_deps, plugin_dir, plugin_info, ctx.outputs.plugin_mac)
    _studio_plugin_os(ctx, WIN, module_deps, plugin_dir, plugin_info, ctx.outputs.plugin_win)

    return struct(
        directory = ctx.attr.directory,
        xml = plugin_xml,
        xml_jar = plugin_dir + "/lib/" + plugin_jar,
        files = depset(),
        files_linux = depset([ctx.outputs.plugin_linux]),
        files_mac = depset([ctx.outputs.plugin_mac]),
        files_win = depset([ctx.outputs.plugin_win]),
        plugin_info = plugin_info,
    )

_studio_plugin = rule(
    attrs = {
        "modules": attr.label_list(allow_empty = False),
        "jars": attr.string_list(),
        "resources": attr.label_list(allow_files = True),
        "resources_dirs": attr.string_list(),
        "directory": attr.string(),
        "compress": attr.bool(),
        "deps": attr.label_list(),
        "_singlejar": attr.label(
            default = Label("@bazel_tools//tools/jdk:singlejar"),
            cfg = "host",
            executable = True,
        ),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "host",
            executable = True,
        ),
        "_check_plugin": attr.label(
            default = Label("//tools/adt/idea/studio:check_plugin"),
            cfg = "host",
            executable = True,
        ),
    },
    outputs = {
        "plugin_linux": "%{name}.linux.zip",
        "plugin_mac": "%{name}.mac.zip",
        "plugin_win": "%{name}.win.zip",
    },
    implementation = _studio_plugin_impl,
)

def _is_release():
    return select({
        "//tools/base/bazel:release": True,
        "//conditions:default": False,
    })

# Build an Android Studio plugin.
# This plugin is a zip file with the final layout inside Android Studio plugin's directory.
# Args
#    name: The id of the plugin (eg. intellij.android.plugin)
#    directory: The directory to use inside plugins (eg. android)
#    modules: A dictionary of the form
#             {"name.jar": ["m1" , "m2"]}
#             Where keys are the names of the jars in the libs directory, and the values
#             are the list of modules that will be in that jar.
#    resources: A dictionary of the form
#             {"dir": <files> }
#             where keys are the directories where to place the resources, and values
#             is a list of files to place there (it supports studio_data rules)
def studio_plugin(
        name,
        directory,
        modules = {},
        resources = {},
        **kwargs):
    jars, modules_list = _dict_to_lists(modules)
    resources_dirs, resources_list = _dict_to_lists(resources)

    _studio_plugin(
        name = name,
        directory = directory,
        modules = modules_list,
        jars = jars,
        resources = resources_list,
        resources_dirs = resources_dirs,
        compress = _is_release(),
        **kwargs
    )

def _studio_data_impl(ctx):
    for dep in ctx.attr.files_linux + ctx.attr.files_mac + ctx.attr.files_win:
        if hasattr(dep, "mappings"):
            fail("studio_data does not belong on a platform specific attribute, please add " + str(dep.label) + " to \"files\" directly")

    files = []
    mac = []
    win = []
    linux = []
    mappings = {}
    to_map = []
    for dep in ctx.attr.files:
        files += [dep.files]
        if hasattr(dep, "mappings"):
            linux += [dep.files_linux]
            mac += [dep.files_mac]
            win += [dep.files_win]
            mappings.update(dep.mappings)
        else:
            to_map += dep.files.to_list()

    for prefix, destination in ctx.attr.mappings.items():
        for src in to_map + ctx.files.files_mac + ctx.files.files_linux + ctx.files.files_win:
            if src not in mappings and src.short_path.startswith(prefix):
                mappings[src] = destination + src.short_path[len(prefix):]

    dfiles = depset(order = "preorder", transitive = files)
    dlinux = depset(ctx.files.files_linux, order = "preorder", transitive = linux)
    dmac = depset(ctx.files.files_mac, order = "preorder", transitive = mac)
    dwin = depset(ctx.files.files_win, order = "preorder", transitive = win)

    return struct(
        files = dfiles,
        files_linux = dlinux,
        files_mac = dmac,
        files_win = dwin,
        mappings = mappings,
    )

_studio_data = rule(
    attrs = {
        "files": attr.label_list(allow_files = True),
        "files_linux": attr.label_list(allow_files = True),
        "files_mac": attr.label_list(allow_files = True),
        "files_win": attr.label_list(allow_files = True),
        "mappings": attr.string_dict(
            mandatory = True,
            allow_empty = False,
        ),
    },
    executable = False,
    implementation = _studio_data_impl,
)

# A specialized version of a filegroup, that groups all the given files but also provides different
# sets of files for each platform.
# This allows grouping all files of the same concept that have different platform variants.
# Args:
#     files: A list of files present on all platforms
#     files_{linux, mac, win}: A list of files for each platform
#     mapping: A dictionary to map file locations and build an arbitrary file tree, in the form of
#              a dictionary from current directory to new directory.
def studio_data(name, files = [], files_linux = [], files_mac = [], files_win = [], mappings = {}, tags = [], **kwargs):
    _studio_data(
        name = name,
        files = files,
        files_linux = files_linux,
        files_mac = files_mac,
        files_win = files_win,
        mappings = mappings,
        tags = tags,
        **kwargs
    )

def _stamp(ctx, platform, zip, extra, srcs, out):
    args = ["--platform", zip.path]
    args += ["--os", platform.name]
    args += ["--version_file", ctx.version_file.path]
    args += ["--info_file", ctx.info_file.path]
    args += ["--eap", "true" if ctx.attr.version_eap else "false"]
    args += ["--version_micro", str(ctx.attr.version_micro)]
    args += ["--version_patch", str(ctx.attr.version_patch)]
    args += ["--version_full", ctx.attr.version_full]
    args += extra
    ctx.actions.run(
        inputs = [zip, ctx.info_file, ctx.version_file] + srcs,
        outputs = [out],
        executable = ctx.executable._stamper,
        arguments = args,
        progress_message = "Stamping %s file..." % zip.basename,
        mnemonic = "stamper",
    )

def _stamp_platform(ctx, platform, zip, out):
    args = ["--stamp_platform", out.path]
    _stamp(ctx, platform, zip, args, [], out)

def _stamp_platform_plugin(ctx, platform, zip, src, dst):
    args = ["--stamp_platform_plugin", src.path, dst.path]
    _stamp(ctx, platform, zip, args, [src], dst)

def _stamp_plugin(ctx, platform, zip, src, dst):
    args = ["--stamp_plugin", src.path, dst.path]
    _stamp(ctx, platform, zip, args, [src], dst)

def _zip_merger(ctx, zips, overrides, out):
    files = [f for (p, f) in zips + overrides]
    zipper_files = [r + "=" + f.path + "\n" for r, f in zips]
    zipper_files += [r + "=+" + f.path + "\n" for r, f in overrides]
    zipper_args = ["cC" if ctx.attr.compress else "c", out.path]
    zipper_list = create_option_file(ctx, out.basename + ".res.lst", "".join(zipper_files))
    zipper_args += ["@" + zipper_list.path]
    ctx.actions.run(
        inputs = files + [zipper_list],
        outputs = [out],
        executable = ctx.executable._zip_merger,
        arguments = zipper_args,
        progress_message = "Creating distribution zip...",
        mnemonic = "zipmerger",
    )

def _codesign(ctx, filelist_template, entitlements, prefix, out):
    filelist = ctx.actions.declare_file(ctx.attr.name + ".codesign.filelist")
    ctx.actions.expand_template(
        template = filelist_template,
        output = filelist,
        substitutions = {
            "%prefix%": prefix,
        },
    )

    ctx.actions.declare_file(ctx.attr.name + ".codesign.zip")
    files = [
        ("_codesign/filelist", filelist),
        ("_codesign/entitlements.xml", entitlements),
    ]

    _zipper(ctx, "_codesign for macOS", files, out)

def _android_studio_prefix(ctx, platform):
    if platform == MAC:
        return ctx.attr.platform.mac_bundle_name + "/"
    return "android-studio/"

def _android_studio_os(ctx, platform, out):
    files = []
    zips = []
    overrides = []

    platform_prefix = _android_studio_prefix(ctx, platform)

    platform_zip = platform.get(ctx.attr.platform.data)[0]

    platform_plugins = platform.get(ctx.attr.platform.plugins)
    zips += [(platform_prefix, zip) for zip in [platform_zip] + platform_plugins]
    if ctx.attr.jre:
        jre_zip = ctx.actions.declare_file(ctx.attr.name + ".jre.%s.zip" % platform.name)
        jre_files = [(ctx.attr.jre.mappings[f], f) for f in platform.get(ctx.attr.jre)]
        _zipper(ctx, "%s jre" % platform.name, jre_files, jre_zip)
        zips += [(platform_prefix + platform.base_path + platform.jre, jre_zip)]

    # Stamp the platform and its plugins
    platform_stamp = ctx.actions.declare_file(ctx.attr.name + ".%s.platform.stamp.zip" % platform.name)
    _stamp_platform(ctx, platform, platform_zip, platform_stamp)
    overrides += [(platform_prefix, platform_stamp)]
    for plugin in platform_plugins:
        stamp = ctx.actions.declare_file(ctx.attr.name + ".stamp.%s" % plugin.basename)
        _stamp_platform_plugin(ctx, platform, platform_zip, plugin, stamp)
        overrides += [(platform_prefix, stamp)]

    res = _resource_deps(ctx.attr.resources_dirs, ctx.attr.resources, platform)
    files += [(platform.base_path + d, f) for (d, f) in res]

    dev01 = ctx.actions.declare_file(ctx.attr.name + ".dev01." + platform.name)
    ctx.actions.write(dev01, "")
    files += [(platform.base_path + "license/dev01_license.txt", dev01)]

    module_deps, _, _ = _module_deps(ctx, ctx.attr.jars, ctx.attr.modules)
    files += [(platform.base_path + "lib/" + d, f) for (d, f) in module_deps]

    searchable_options = {}
    for dep, spec in ctx.attr.searchable_options.items():
        plugin, jar = spec.split("/")
        if plugin not in searchable_options:
            searchable_options[plugin] = {}
        if jar not in searchable_options[plugin]:
            searchable_options[plugin][jar] = []
        searchable_options[plugin][jar] += dep.files.to_list()

    so_jars = []
    for plugin, jars in searchable_options.items():
        for jar, so_files in jars.items():
            so_jar = ctx.actions.declare_file(ctx.attr.name + ".so.%s.%s.%s.zip" % (platform.name, plugin, jar))
            _zipper(ctx, "%s %s searchable options" % (plugin, jar), [("search/%s" % f.basename, f) for f in so_files], so_jar)
            so_jars += [("%splugins/%s/lib/%s" % (platform.base_path, plugin, jar), so_jar)]
    so_extras = ctx.actions.declare_file(ctx.attr.name + ".so.%s.zip" % platform.name)
    _zipper(ctx, "%s searchable options" % platform.name, so_jars, so_extras)
    overrides += [(platform_prefix, so_extras)]

    extras_zip = ctx.actions.declare_file(ctx.attr.name + ".extras.%s.zip" % platform.name)
    _zipper(ctx, "%s extras" % platform.name, files, extras_zip)
    zips += [(platform_prefix, extras_zip)]

    for p in ctx.attr.plugins:
        plugin_zip = platform.get(p)[0]
        stamp = ctx.actions.declare_file(ctx.attr.name + ".stamp.%s" % plugin_zip.basename)
        _stamp_plugin(ctx, platform, platform_zip, plugin_zip, stamp)
        overrides += [(platform_prefix + platform.base_path, stamp)]
        zips += [(platform_prefix + platform.base_path, plugin_zip)]

    if platform == MAC:
        codesign = ctx.actions.declare_file(ctx.attr.name + ".codesign.zip")
        _codesign(ctx, ctx.file.codesign_filelist, ctx.file.codesign_entitlements, platform_prefix, codesign)
        zips += [("", codesign)]

    _zip_merger(ctx, zips, overrides, out)

def _android_studio_impl(ctx):
    plugins = [plugin.directory for plugin in ctx.attr.plugins]
    ctx.actions.write(ctx.outputs.plugins, "".join([dir + "\n" for dir in plugins]))

    _android_studio_os(ctx, LINUX, ctx.outputs.linux)
    _android_studio_os(ctx, MAC, ctx.outputs.mac)
    _android_studio_os(ctx, WIN, ctx.outputs.win)

    # Leave everything that is not the main zips as implicit outputs
    return DefaultInfo(files = depset([ctx.outputs.linux, ctx.outputs.mac, ctx.outputs.win]))

_android_studio = rule(
    attrs = {
        "platform": attr.label(),
        "jre": attr.label(),
        "modules": attr.label_list(),
        "jars": attr.string_list(),
        "resources": attr.label_list(),
        "resources_dirs": attr.string_list(),
        "plugins": attr.label_list(),
        "searchable_options": attr.label_keyed_string_dict(allow_files = True),
        "version_micro": attr.int(),
        "version_patch": attr.int(),
        "version_eap": attr.bool(),
        "version_full": attr.string(),
        "compress": attr.bool(),
        "codesign_filelist": attr.label(allow_single_file = True),
        "codesign_entitlements": attr.label(allow_single_file = True),
        "_singlejar": attr.label(
            default = Label("@bazel_tools//tools/jdk:singlejar"),
            cfg = "host",
            executable = True,
        ),
        "_zip_merger": attr.label(
            default = Label("//tools/base/bazel:zip_merger"),
            cfg = "host",
            executable = True,
        ),
        "_stamper": attr.label(
            default = Label("//tools/adt/idea/studio:stamper"),
            cfg = "host",
            executable = True,
        ),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "host",
            executable = True,
        ),
        "_unzipper": attr.label(
            default = Label("//tools/base/bazel:unzipper"),
            cfg = "host",
            executable = True,
        ),
    },
    outputs = {
        "linux": "%{name}.linux.zip",
        "mac": "%{name}.mac.zip",
        "win": "%{name}.win.zip",
        "plugins": "%{name}.plugin.lst",
    },
    implementation = _android_studio_impl,
)

# Builds a distribution of android studio.
# Args:
#       platform: A studio_data target with the per-platform filegroups
#       jre: If include a target with the jre to bundle in.
#       plugins: A list of plugins to be bundled
#       modules: A dictionary (see studio_plugin) with modules bundled at top level
#       resources: A dictionary (see studio_plugin) with resources bundled at top level
def android_studio(
        name,
        searchable_options,
        modules = {},
        resources = {},
        **kwargs):
    jars, modules_list = _dict_to_lists(modules)
    resources_dirs, resources_list = _dict_to_lists(resources)
    searchable_options_dict = {}
    for rel_path in native.glob([searchable_options + "/**"]):
        parts = rel_path.split("/")
        if len(parts) > 3:
            searchable_options_dict[rel_path] = parts[1] + "/" + parts[2]

    _android_studio(
        name = name,
        modules = modules_list,
        jars = jars,
        resources = resources_list,
        resources_dirs = resources_dirs,
        searchable_options = searchable_options_dict,
        compress = _is_release(),
        **kwargs
    )

def _intellij_plugin_impl(ctx):
    infos = []
    for jar in ctx.files.jars:
        ijar = java_common.run_ijar(
            actions = ctx.actions,
            jar = jar,
            java_toolchain = find_java_toolchain(ctx, ctx.attr._java_toolchain),
        )
        infos.append(JavaInfo(
            output_jar = jar,
            compile_jar = ijar,
        ))
    plugin_info = _check_plugin(ctx, ctx.files.jars)
    return struct(
        providers = [java_common.merge(infos)],
        plugin_info = plugin_info,
    )

_intellij_plugin = rule(
    attrs = {
        "jars": attr.label_list(allow_files = True),
        "_java_toolchain": attr.label(default = Label("@bazel_tools//tools/jdk:current_java_toolchain")),
        "_check_plugin": attr.label(
            default = Label("//tools/adt/idea/studio:check_plugin"),
            cfg = "host",
            executable = True,
        ),
    },
    implementation = _intellij_plugin_impl,
)

def _intellij_platform_impl_os(ctx, platform, data):
    files = platform.get(data)
    plugin_dir = "%splugins/" % platform.base_path
    base = []
    plugins = {}
    for file in files:
        rel = data.mappings[file]
        if not rel.startswith(plugin_dir):
            # This is not a plugin file
            base.append((rel, file))
            continue
        parts = rel[len(plugin_dir):].split("/")
        if len(parts) == 0:
            fail("Unexpected plugin file: " + rel)
        plugin = parts[0]
        if plugin not in plugins:
            plugins[plugin] = []
        plugins[plugin].append((rel, file))

    base_zip = ctx.actions.declare_file("%s.%s.zip" % (ctx.label.name, platform.name))
    _zipper(ctx, "base %s platform zip" % platform.name, base, base_zip)

    plugin_zips = []
    for plugin, files in plugins.items():
        plugin_zip = ctx.actions.declare_file("%s.plugin.%s.%s.zip" % (ctx.label.name, plugin, platform.name))
        _zipper(ctx, "platform plugin %s %s zip" % (plugin, platform.name), files, plugin_zip)
        plugin_zips.append(plugin_zip)
    return base_zip, plugin_zips

def _intellij_platform_impl(ctx):
    base_linux, plugins_linux = _intellij_platform_impl_os(ctx, LINUX, ctx.attr.data)
    base_win, plugins_win = _intellij_platform_impl_os(ctx, WIN, ctx.attr.data)
    base_mac, plugins_mac = _intellij_platform_impl_os(ctx, MAC, ctx.attr.data)

    return struct(
        files = depset([base_linux, base_mac, base_win]),
        data = struct(
            files = depset([]),
            files_linux = depset([base_linux]),
            files_mac = depset([base_mac]),
            files_win = depset([base_win]),
            mappings = {},
        ),
        plugins = struct(
            files = depset([]),
            files_linux = depset(plugins_linux),
            files_mac = depset(plugins_mac),
            files_win = depset(plugins_win),
            mappings = {},
        ),
        mac_bundle_name = ctx.attr.mac_bundle_name,
    )

_intellij_platform = rule(
    attrs = {
        "data": attr.label(),
        "compress": attr.bool(),
        "mac_bundle_name": attr.string(),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "host",
            executable = True,
        ),
    },
    implementation = _intellij_platform_impl,
)

def intellij_platform(
        name,
        src,
        spec,
        **kwargs):
    native.java_import(
        name = name,
        jars = select({
            "//tools/base/bazel:windows": [src + "/windows/android-studio" + jar for jar in spec.jars + spec.jars_windows],
            "//tools/base/bazel:darwin": [src + "/darwin/android-studio/Contents" + jar for jar in spec.jars + spec.jars_darwin],
            "//conditions:default": [src + "/linux/android-studio" + jar for jar in spec.jars + spec.jars_linux],
        }),
        visibility = ["//visibility:public"],
        # Local linux sandbox does not support spaces in names, so we exclude some files
        # Otherwise we get: "link or target filename contains space"
        data = select({
            "//tools/base/bazel:windows": native.glob(
                include = [src + "/windows/android-studio/**"],
                exclude = [src + "/windows/android-studio/plugins/textmate/lib/bundles/**"],
            ),
            "//tools/base/bazel:darwin": native.glob(
                include = [src + "/darwin/android-studio/**"],
                exclude = [src + "/darwin/android-studio/Contents/plugins/textmate/lib/bundles/**"],
            ),
            "//conditions:default": native.glob(
                include = [src + "/linux/android-studio/**"],
                exclude = [src + "/linux/android-studio/plugins/textmate/lib/bundles/**"],
            ),
        }),
    )

    # Expose lib/resources.jar as a separate target
    native.java_import(
        name = name + "-resources-jar",
        jars = select({
            "//tools/base/bazel:windows": [src + "/windows/android-studio/lib/resources.jar"],
            "//tools/base/bazel:darwin": [src + "/darwin/android-studio/Contents/lib/resources.jar"],
            "//conditions:default": [src + "/linux/android-studio/lib/resources.jar"],
        }),
        visibility = ["//visibility:public"],
    )

    # Expose build.txt from the prebuilt SDK
    native.filegroup(
        name = name + "-build-txt",
        srcs = select({
            "//tools/base/bazel:windows": [src + "/windows/android-studio/build.txt"],
            "//tools/base/bazel:darwin": [src + "/darwin/android-studio/Contents/build.txt"],
            "//conditions:default": [src + "/linux/android-studio/build.txt"],
        }),
        visibility = ["//visibility:public"],
    )

    studio_data(
        name = name + ".data",
        files_linux = native.glob([src + "/linux/**"]),
        files_mac = native.glob([src + "/darwin/**"]),
        files_win = native.glob([src + "/windows/**"]),
        mappings = {
            "prebuilts/studio/intellij-sdk/%s/linux/android-studio/" % src: "",
            "prebuilts/studio/intellij-sdk/%s/darwin/android-studio/" % src: "",
            "prebuilts/studio/intellij-sdk/%s/windows/android-studio/" % src: "",
        },
    )

    for plugin, jars in spec.plugin_jars.items():
        add_windows = spec.plugin_jars_windows[plugin] if plugin in spec.plugin_jars_windows else []
        add_darwin = spec.plugin_jars_darwin[plugin] if plugin in spec.plugin_jars_darwin else []
        add_linux = spec.plugin_jars_linux[plugin] if plugin in spec.plugin_jars_linux else []

        _intellij_plugin(
            name = name + "-plugin-%s" % plugin,
            jars = select({
                "//tools/base/bazel:windows": [src + "/windows/android-studio/plugins/" + plugin + "/lib/" + jar for jar in jars + add_windows],
                "//tools/base/bazel:darwin": [src + "/darwin/android-studio/Contents/plugins/" + plugin + "/lib/" + jar for jar in jars + add_darwin],
                "//conditions:default": [src + "/linux/android-studio/plugins/" + plugin + "/lib/" + jar for jar in jars + add_linux],
            }),
            visibility = ["//visibility:public"],
        )

    native.java_import(
        name = name + "-updater",
        jars = [src + "/updater-full.jar"],
        visibility = ["//visibility:public"],
    )

    _intellij_platform(
        name = name + ".platform",
        compress = _is_release(),
        data = name + ".data",
        mac_bundle_name = spec.mac_bundle_name,
        visibility = ["//visibility:public"],
    )

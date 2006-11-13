# -*-ruby-*-

class CCompilerEnv
  ## These are the defaults, this way, overrides can append
  ## parameters to the defaults if they want to.
  Defines       = "-D_GNU_SOURCE -D_REENTRANT"
  Warnings      = "-Wall"

  CC            = "gcc"
  Ranlib        = "ranlib"
  Archive       = "ar"
  Loader        = "ld"
  Link          = "ln"

  ## Debugging packages
  Mvutil        = 255
  Netcap        = 201
  Vector        = 202
  JMvutil       = 211
  JNetcap       = 212
  JVector       = 214
  
  def initialize( overrides = {} )
    @defines       = Defines
    @warnings      = Warnings
    @optimizations = CCompilerEnv.defaultOptimizations
    
    @cc            = CC
    @ranlib        = Ranlib
    @archive       = Archive
    @loader        = Loader
    @link          = Link
    @strip         = CCompilerEnv.defaultStripCommand
    @flags         = CCompilerEnv.defaultDebugFlags
    @pkg           = ""
    @version       = ""

    overrides.each_pair do |key,value|
      eval( "@#{key}=value" )
    end
  end

  ## Get the flags for an object file
  ## includeDirectories is an array of directories to look for 
  ## header files.
  def objectFlags( includeDirectories )
    includeDirectories = [ includeDirectories ].flatten

    includes = includeDirectories.map{ |d| "-I#{d}" }
    
    [ flags, defines, warnings, optimizations, includes ].flatten.join( " " )
  end

  def linkerFlags( libDirectories, libArray )
    directories = libDirectories.map{ |d| "-L#{d}" }
    libs = libArray.map{ |l| "-l#{l}" }
    
    [ flags, warnings, optimizations, directories, libs ].flatten.join( " " )
  end

  def defines
    value=  "#{@defines}"
    value+= " -DDEBUG_PKG=#{@pkg}" unless ( @pkg == "" )
    value+= " -DVERSION=\\\"#{@version}\\\"" unless ( @version == "" )
    return value
  end

  def CCompilerEnv.defaultDebugFlags
    ( $DevelBuild ) ?  "-g -ggdb -DDEBUG_ON" : ""
  end

  def CCompilerEnv.defaultOptimizations
    ( $DevelBuild ) ? "" : "-funroll-loops -fomit-frame-pointer"
  end

  def CCompilerEnv.defaultStripCommand
    ( $DevelBuild ) ? "echo \"not stripping symbols for: \"" : "strip --strip-debug --remove-section=.note --remove-section=.comment -x"
  end

  attr_reader     :warnings, :optimizations, :cc, :ranlib
  attr_reader     :archive, :linker, :link, :strip, :flags, :pkg, :version
end

## Action used to compile C source files object files, object files into archives and shared libraries.
class CBuilder
  ## A little awkward the includes are defined here, but libraries are defined in makeLibrary
  def initialize( env, includes = [] )
    @env        = env
    @objectArgs = [ @env.objectFlags( includes + [ $BuildEnv.include ] ) ].flatten
  end

  def makeObject( sourceFile, objectFile )
    info "gcc #{sourceFile} => #{objectFile}"
    cmd = "#{@env.cc} #{@objectArgs} -c #{sourceFile} -o #{objectFile}"
    
    debug "cmd: #{cmd}"
    raise "gcc failed" unless Kernel.system( cmd )
  end
  
  def makeArchive( source, destination )
    info "Archive: #{source} => #{destination}"
    
    fileList = FileList[ "#{source}/*.o" ]
    
    raise "#{@env.archive} failed" unless Kernel.system( @env.archive, "-cr", destination, *fileList )
    raise "#{@env.ranlib} failed"  unless Kernel.system( @env.ranlib, destination )
  end

  ## source: Array of object and archive files for the shared object
  ## destination: Name of the file where the shared library should be created.
  ## libDir: Search paths for libraries that are not in the standard location
  ## lib: Array of the libraries to include in the compilation.
  ## wLib: Array of the libraries that should be included in their entirety.
  def makeSharedLibrary( source, destination, libDir, lib, wLib = [] )
    info "SharedLibrary: #{destination}"
    wLibFlags = wLib.map{ |l| "-l#{l}" }.join( " " )
    flags = @env.linkerFlags( libDir, lib )
    cmd = "#{@env.cc} #{flags} -Wl,-whole-archive #{source.join( " " )} #{wLibFlags} -Wl,--no-whole-archive -shared -o #{destination}"

    debug cmd
    raise "gcc failed" unless Kernel.system( cmd )

    cmd = "#{@env.strip} #{destination}"
    debug cmd
    raise "unable to strip symbols" unless Kernel.system( cmd )
  end

  ## source: Array of object and archive files for the shared object
  ## destination: Name of the file where the shared library should be created.
  ## libDir: Search paths for libraries that are not in the standard location
  ## lib: Array of the libraries to include in the compilation.
  ## wLib: Array of the libraries that should be included in their entirety.
  def makeBinary( source, destination, libDir, lib, wLib = [] )
    info "Binary: #{destination}"
    wLibFlags = wLib.map{ |l| "-l#{l}" }.join( " " )
    flags = @env.linkerFlags( libDir, lib )
    cmd = "#{@env.cc} #{flags} -Wl,-whole-archive #{source.join( " " )} #{wLibFlags} -Wl,--no-whole-archive -o #{destination}"
    
    debug cmd
    raise "gcc failed" unless Kernel.system( cmd )
  end
end

class CCompilerTarget < Target
  def initialize(package, deps, builder, buildDirectory, basepaths)
    @targetName = "c-compiler:#{package.name}"

    @builder = builder

    ## Force basepath to be an array
    @basepaths = [ basepaths ].flatten

    @buildDirectory = buildDirectory

    ## Doesn't support nested directories.
    @sourceFiles = FileList[ @basepaths.map { |basepath| "#{basepath}/*.c"} ]

    super(package,deps)
  end

  def makeDependencies
    if 0 == @sourceFiles.length
      warn "#{self} has no input files."
      return
    end

    @sourceFiles.each do |sourceFile|
      objectFile =  "#{@buildDirectory}/#{File.basename( sourceFile ).gsub( /.c$/, ".o" )}"
      debug "Building dependency for #{sourceFile} => #{objectFile}"

      ## Make the classfile depend on the source itself
      file objectFile => sourceFile do
        ensureDirectory( @buildDirectory )
        @builder.makeObject( sourceFile, objectFile )
      end

      # Make the output this target dependent on the object file
      stamptask self => objectFile
    end
  end

  ## Nothing to build
  def build
    puts "Completing building #{@targetName}"
  end

  def to_s
    @targetName
  end

  def includes
    @includes
  end
end

class ArchiveTarget < Target
  def initialize( package, deps, destination, basepaths, builder )
    @targetName = "archive:#{package.name}"
    @builder = builder
    @destination = destination
    
    ## Force basepaths to be an array
    @basepaths = [ basepaths ].flatten
    
    super(package, deps, "archive")
  end

  def build
    @builder.makeArchive(@basepaths, @destination)
  end
  
  ## package: Name of the package to build an archive for (this should share the name of the
  ## directory that the files are in
  ## deps: Dependencies for this archive
  ## env: Build environment for this archive
  ## includes: Any additional include directories required to build this archive.
  def ArchiveTarget.buildTarget(package, deps, env, includes = [] )
    directory = package.name

    ## Create a builder
    sourceDirectory = "#{directory}/src"
    
    includes = [ includes ].flatten

    deps.each do |d|
      next unless d.kind_of? JavahTarget
      includes << d.javahDirectory
    end

    builder = CBuilder.new( env, includes )
    
    ## Each package really only has one c-compile, no
    ## need for a suffix
    buildDirectory = "#{$BuildEnv.staging}/#{package.name}/obj"
    includeDirectory = "#{$BuildEnv.include}"
    
    ## Make a copyfiles target to copy in all of the includes.
    ms = MoveSpec.new("#{directory}/include", "**/*.h",includeDirectory)
    copyTarget = CopyFiles.new(package,ms,"includes")
    
    compilerTarget = CCompilerTarget.new(package, deps, builder, buildDirectory, sourceDirectory )
    ## Make a c-compiler target that depends on the dependencies
    archiveDeps =  [ copyTarget, compilerTarget ]
    
    destination = "#{$BuildEnv.staging}/#{package.name}/#{package.name}.a"
    
    ## Make an archive that depens on the CCompilerTarget
    ArchiveTarget.new(package,archiveDeps,destination,buildDirectory,builder)
  end
  
  def filename
    @destination
  end

  def to_s
    "archive:#{@destination}"
  end
end

class JavahTarget < Target
  def initialize(package, jar, classes)
    @jar = jar
    @classes = classes
    @javahDirectory = "#{$BuildEnv.staging}/#{package.name}/jni_headers"
    super(package,[ jar ])
  end

  def build
    JavaCompiler.javah(@jar.filename,@javahDirectory, @classes)
  end

  def javahDirectory
    @javahDirectory
  end
  
  def to_s
    "javah:#{@jar}"
  end
end


def getVersion(package)
  version = ""
  File.open( "#{package.name}/VERSION" ) { |f| version=f.gets.strip }
  version
end

# -*-ruby-*-

class JasperCompiler
  JavaCommand = "#{$BuildEnv.javahome}/bin/java"
  BuildUtilPkg = "com.metavize.buildutil"
  ReportGenerator = "#{BuildUtilPkg}.ReportGenerator"
  JRCompiler = "#{BuildUtilPkg}.JRCompiler"

  TemplateDirectory = "mvvm/resources/reports"

  include Singleton
  
  ## Convert an rpd file 
  def JasperCompiler.rpdToJrxml( fileList, destination )
    if ( fileList.size > 0 ) 
      raise "ReportGenerator failed" unless
        Kernel.system( JavaCommand, "-cp", classpath, ReportGenerator, "-t", TemplateDirectory, 
                       "-o", destination, *fileList )
    end
  end
  
  def JasperCompiler.jrxmlToJasper( fileList, destination = "" )
    if ( fileList.size > 0 ) 
      raise "JRCompiler failed" unless 
        Kernel.system( JavaCommand, "-cp", classpath, JRCompiler, "-o", destination, *fileList )
    end
  end
  
  private
  def JasperCompiler.classpath
    [ Jars::Reporting + [ Package['buildutil']['impl'].filename ]].flatten.join(":")
  end
end

## This is a target for converting a list of RPD files into JRXML files
class JRXMLTarget < Target
  def initialize( package, buildDirectory, basepaths )
    @targetName = "rpdtojrxml:#{package.name}"
    
    ## Force basepath to be an array
    @basepaths = [ basepaths ].flatten

    @buildDirectory = buildDirectory
    
    @rpdFiles = FileList[ @basepaths.map { |basepath| "#{basepath}/**/*.rpd"} ]
        
    ## There are no dependencies for these files
    super( package )
  end

  def makeDependencies
    ## ReportGenerator is built inside of the build utils
    buildutil = Package['buildutil']

    @rpdFiles.each do |f| 
      jrxmlFile =  "#{@buildDirectory}/#{File.basename( f ).gsub(/.rpd$/,".jrxml")}"
      debug jrxmlFile

      ## Make the classfile depend on the source itself
      file jrxmlFile => f do
        directory = File.dirname jrxmlFile
        ## XXX Hokey way to update the timestamp XXX
        mkdir_p directory if !File.exist?(directory)
        Kernel.system("touch",jrxmlFile)
      end

      file jrxmlFile => buildutil

      # Make the stamp task
      stamptask self => jrxmlFile
    end
  end
  
  def build
    JasperCompiler.rpdToJrxml( @rpdFiles, @buildDirectory )
  end
    
  def to_s
    @targetName
  end
  
end

## This is a target for converting a list of JRXML files into a Jasper file
class JasperTarget < Target
  def initialize(package, deps, buildDirectory, basepaths )
    @targetName = "jrxmltojasper:#{package.name}"
    
    ## Force basepath to be an array
    @basepaths = [ basepaths ].flatten

    @buildDirectory = buildDirectory

    @jrxmlFiles = FileList[ @basepaths.map { |basepath| "#{basepath}/**/*.jrxml"} ]

    super(package,deps)
  end

  def makeDependencies
    @jrxmlFiles.each { |f| stamptask self => f }
  end

  def build
    ## Have to re-evaluate in order to get the RPD files
    jrxmlFiles = FileList[ @basepaths.map { |basepath| "#{basepath}/**/*.jrxml"} ]

    JasperCompiler.jrxmlToJasper( jrxmlFiles, @buildDirectory )
  end

  def to_s
    @targetName
  end

  def JasperTarget.buildTarget( package, destination, basepaths )
    basepaths = [ basepaths ].flatten

    ## Build all of the JRXML files from the RPDs
    rpdDirectory = "#{$BuildEnv.staging}/#{package.name}-rpd"
    deps = [ JRXMLTarget.new( package, rpdDirectory, basepaths ) ]

    ## Append the Generated JRXML files to the base paths
    basepaths <<  rpdDirectory
    JasperTarget.new( package, deps, destination, basepaths )
  end
end

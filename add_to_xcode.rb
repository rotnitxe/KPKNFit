require 'rubygems'
require 'xcodeproj'

project_path = 'ios-native/KPKNFit/KPKNFit.xcodeproj'
project = Xcodeproj::Project.open(project_path)
target = project.targets.find { |t| t.name == 'KPKNFit' }

def add_file_to_project(project, target, file_name, group_path)
  group_names = group_path.split('/')
  current_group = project.main_group
  
  group_names.each do |name|
    next_group = current_group.groups.find { |g| g.display_name == name || g.path == name }
    if next_group.nil?
      next_group = current_group.new_group(name, name)
    end
    current_group = next_group
  end
  
  old_ref = current_group.files.find { |f| f.display_name == file_name || f.path == file_name || f.name == file_name }
  if old_ref
    # Already exists
    return
  end
  
  file_ref = current_group.new_file(file_name)
  file_ref.set_path("#{group_path}/#{file_name}")
  file_ref.source_tree = 'SOURCE_ROOT'
  
  target.source_build_phase.add_file_reference(file_ref)
  puts "Added #{file_name} to #{group_path} successfully"
end

# Scan recursively for all Swift files in KPKNFit app directory
Dir.glob('ios-native/KPKNFit/KPKNFit/**/*.swift') do |path|
  relative_path = path.sub('ios-native/KPKNFit/', '')
  parts = relative_path.split('/')
  file_name = parts.pop
  group_path = parts.join('/')
  
  add_file_to_project(project, target, file_name, group_path)
end

project.save
puts "Project auto-sync complete!"

package com.google.api.graphql.rejoiner;

import com.google.protobuf.DescriptorProtos;

import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

class DescriptorSet {
  private static final String DEFAULT_DESCRIPTOR_SET_FILE_LOCATION = "META-INF/proto/descriptor_set.desc";

  static final Map<String, String> COMMENTS = getCommentsFromDescriptorFile();

  private DescriptorSet() {
  }

  private static Map<String, String> getCommentsFromDescriptorFile() {

    try {
      InputStream is = DescriptorSet.class.getClassLoader().getResourceAsStream(DEFAULT_DESCRIPTOR_SET_FILE_LOCATION);
      DescriptorProtos.FileDescriptorSet descriptors = DescriptorProtos.FileDescriptorSet.parseFrom(is);
      return descriptors
          .getFileList()
          .stream()
          .flatMap(fileDescriptorProto -> parseDescriptorFile(fileDescriptorProto)
              .entrySet()
              .stream()
          )
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (entry, value) -> entry));
    } catch (IOException ignored) {
    }
    return Collections.emptyMap();
  }

  private static Map<String, String> parseDescriptorFile(DescriptorProtos.FileDescriptorProto descriptor) {
    return descriptor
        .getSourceCodeInfo()
        .getLocationList()
        .stream()
        .filter(location -> !(location.getLeadingComments().isEmpty() && location.getTrailingComments().isEmpty()))
        .map(location -> getFullName(descriptor, location.getPathList())
            .map(fullName -> new AbstractMap.SimpleImmutableEntry<>(fullName, constructComment(location)))
        )
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static String constructComment(DescriptorProtos.SourceCodeInfo.Location location) {
    String trailingComment = location.getTrailingComments().trim();
    return (location.getLeadingComments().trim()
        + (!trailingComment.isEmpty() ? (". " + trailingComment) : "")
    ).replaceAll("^[*\\\\/.]*\\s*", "");
  }

  /**
   * Iterate through a component's path inside a protobufer descriptor.
   * The path is a tuple of component type and a relative position
   * For example:
   * [4, 1, 3, 2, 2, 1] or [MESSAGE_TYPE_FIELD_NUMBER, 1, NESTED_TYPE_FIELD_NUMBER, 2, FIELD_FIELD_NUMBER, 1]
   * is representing the second field of the third nested message in the second message in the file
   * @see DescriptorProtos.SourceCodeInfoOrBuilder for more info
   *
   * @param descriptor proto file descriptor
   * @param path       path of the element
   * @return full element's path as a string
   */
  private static Optional<String> getFullName(DescriptorProtos.FileDescriptorProto descriptor, List<Integer> path) {
    String fullName = descriptor.getPackage();
    switch (path.get(0)) {
      case DescriptorProtos.FileDescriptorProto.ENUM_TYPE_FIELD_NUMBER:
        DescriptorProtos.EnumDescriptorProto enumDescriptor = descriptor.getEnumType(path.get(1));
        return Optional.of(appendEnumToFullName(enumDescriptor, path, fullName));
      case DescriptorProtos.FileDescriptorProto.MESSAGE_TYPE_FIELD_NUMBER:
        DescriptorProtos.DescriptorProto message = descriptor.getMessageType(path.get(1));
        return appendMessageToFullName(message, path, fullName);
      case DescriptorProtos.FileDescriptorProto.SERVICE_FIELD_NUMBER:
        DescriptorProtos.ServiceDescriptorProto serviceDescriptor = descriptor.getService(path.get(1));
        fullName += appendNameComponent(serviceDescriptor.getName());
        if (path.size() > 2) {
          fullName += appendNameComponent(serviceDescriptor.getMethod(path.get(3)).getName());
        }
        return Optional.of(fullName);
      default:
        return Optional.empty();
    }
  }

  private static Optional<String> appendMessageToFullName(
      DescriptorProtos.DescriptorProto message, List<Integer> path, String fullName) {
    fullName += appendNameComponent(message.getName());
    return path.size() > 2 ?
        appendToFullName(message, path.subList(2, path.size()), fullName)
        : Optional.of(fullName);
  }

  private static Optional<String> appendToFullName(
      DescriptorProtos.DescriptorProto messageDescriptor, List<Integer> path, String fullName) {
    switch (path.get(0)) {
      case DescriptorProtos.DescriptorProto.NESTED_TYPE_FIELD_NUMBER:
        DescriptorProtos.DescriptorProto nestedMessage = messageDescriptor.getNestedType(path.get(1));
        return appendMessageToFullName(nestedMessage, path, fullName);
      case DescriptorProtos.DescriptorProto.ENUM_TYPE_FIELD_NUMBER:
        DescriptorProtos.EnumDescriptorProto enumDescriptor = messageDescriptor.getEnumType(path.get(1));
        return Optional.of(appendEnumToFullName(enumDescriptor, path, fullName));
      case DescriptorProtos.DescriptorProto.FIELD_FIELD_NUMBER:
        DescriptorProtos.FieldDescriptorProto fieldDescriptor = messageDescriptor.getField(path.get(1));
        return Optional.of(fullName + appendNameComponent(fieldDescriptor.getName()));
      default:
        return Optional.empty();
    }
  }

  private static String appendEnumToFullName(
      DescriptorProtos.EnumDescriptorProto enumDescriptor, List<Integer> path, String fullName) {
    fullName += appendNameComponent(enumDescriptor.getName());
    if (path.size() > 2) {
      fullName += appendNameComponent(enumDescriptor.getValue(path.get(3)).getName());
    }
    return fullName;
  }

  private static String appendNameComponent(String component) {
    return "." + component;
  }

}
